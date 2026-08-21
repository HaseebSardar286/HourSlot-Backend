package com.hourslot.service;

import com.hourslot.model.Business;
import com.hourslot.model.BusinessVerificationDocument;
import com.hourslot.model.MediaAsset;
import com.hourslot.model.User;
import com.hourslot.repository.BusinessVerificationDocumentRepository;
import com.hourslot.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class VerificationDocumentService {

    public static final Set<String> REQUIRED_TYPES = Set.of(
            BusinessVerificationDocument.TRADE_LICENSE,
            BusinessVerificationDocument.BANK_STATEMENT,
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID
    );

    private static final Map<String, String> TYPE_LABELS = Map.of(
            BusinessVerificationDocument.TRADE_LICENSE, "Trade / business license",
            BusinessVerificationDocument.BANK_STATEMENT, "Bank statement",
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID, "Owner government ID"
    );

    private final BusinessVerificationDocumentRepository documentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final String uploadDir;

    public VerificationDocumentService(
            BusinessVerificationDocumentRepository documentRepository,
            MediaAssetRepository mediaAssetRepository,
            @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.uploadDir = uploadDir;
    }

    public static String labelFor(String type) {
        return TYPE_LABELS.getOrDefault(type, type);
    }

    public List<Map<String, Object>> requiredTypeCatalog() {
        return REQUIRED_TYPES.stream()
                .sorted()
                .map(code -> Map.<String, Object>of("code", code, "label", labelFor(code)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessVerificationDocument> list(Business business) {
        return documentRepository.findByBusinessOrderByCreatedAtDesc(business);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readiness(Business business) {
        List<BusinessVerificationDocument> docs = list(business);
        Map<String, BusinessVerificationDocument> byType = new LinkedHashMap<>();
        for (BusinessVerificationDocument doc : docs) {
            byType.putIfAbsent(doc.getDocumentType(), doc);
        }
        long approved = REQUIRED_TYPES.stream()
                .filter(t -> byType.containsKey(t)
                        && BusinessVerificationDocument.STATUS_APPROVED.equals(byType.get(t).getStatus()))
                .count();
        boolean complete = approved == REQUIRED_TYPES.size();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requiredCount", REQUIRED_TYPES.size());
        result.put("approvedCount", approved);
        result.put("submittedCount", byType.size());
        result.put("readyForVerifiedBadge", complete);
        result.put("missingTypes", REQUIRED_TYPES.stream()
                .filter(t -> !byType.containsKey(t)
                        || !BusinessVerificationDocument.STATUS_APPROVED.equals(byType.get(t).getStatus()))
                .map(t -> Map.of("code", t, "label", labelFor(t)))
                .toList());
        return result;
    }

    @Transactional
    public BusinessVerificationDocument upload(Business business, String documentType, MultipartFile file)
            throws IOException {
        String type = normalizeType(documentType);
        validateFile(file);

        documentRepository.findByBusinessAndDocumentType(business, type).ifPresent(existing -> {
            existing.setDeletedAt(LocalDateTime.now());
            documentRepository.save(existing);
            if (existing.getMediaAsset() != null) {
                existing.getMediaAsset().setDeletedAt(LocalDateTime.now());
                mediaAssetRepository.save(existing.getMediaAsset());
            }
        });

        String stored = storeFile(business.getId(), file);
        MediaAsset asset = mediaAssetRepository.save(MediaAsset.builder()
                .ownerType("BUSINESS")
                .ownerId(business.getId())
                .storageKey("kyc-" + business.getId() + "-" + type + "-" + System.currentTimeMillis())
                .url(stored)
                .mimeType(file.getContentType())
                .bytes(file.getSize())
                .sortOrder(0)
                .build());

        return documentRepository.save(BusinessVerificationDocument.builder()
                .business(business)
                .documentType(type)
                .mediaAsset(asset)
                .originalFilename(file.getOriginalFilename())
                .status(BusinessVerificationDocument.STATUS_SUBMITTED)
                .build());
    }

    @Transactional
    public BusinessVerificationDocument review(
            BusinessVerificationDocument document,
            User reviewer,
            boolean approve,
            String notes) {
        document.setStatus(approve
                ? BusinessVerificationDocument.STATUS_APPROVED
                : BusinessVerificationDocument.STATUS_REJECTED);
        document.setReviewNotes(notes);
        document.setReviewedBy(reviewer);
        document.setReviewedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public void requireReadyForVerifiedBadge(Business business) {
        Map<String, Object> readiness = readiness(business);
        if (!Boolean.TRUE.equals(readiness.get("readyForVerifiedBadge"))) {
            throw new IllegalStateException(
                    "Verified badge requires approved Trade license, Bank statement, and Owner government ID.");
        }
    }

    private String normalizeType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("Document type is required.");
        }
        String type = documentType.trim().toUpperCase(Locale.ROOT);
        if (!REQUIRED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Document type must be TRADE_LICENSE, BANK_STATEMENT, or OWNER_GOVERNMENT_ID.");
        }
        return type;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        boolean ok = contentType.startsWith("image/")
                || contentType.equals("application/pdf")
                || contentType.equals("application/msword")
                || contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        if (!ok) {
            throw new IllegalArgumentException("Upload a PDF, Word document, or image.");
        }
        if (file.getSize() > 15L * 1024 * 1024) {
            throw new IllegalArgumentException("File must be 15MB or smaller.");
        }
    }

    private String storeFile(Long businessId, MultipartFile file) throws IOException {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("verification");
        Files.createDirectories(dir);
        String original = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0 && dot < original.length() - 1) {
            ext = original.substring(dot).toLowerCase(Locale.ROOT);
            if (ext.length() > 10) {
                ext = "";
            }
        }
        if (ext.isBlank()) {
            String ct = file.getContentType() != null ? file.getContentType() : "";
            if (ct.contains("pdf")) {
                ext = ".pdf";
            } else if (ct.contains("png")) {
                ext = ".png";
            } else if (ct.contains("webp")) {
                ext = ".webp";
            } else {
                ext = ".bin";
            }
        }
        String filename = "biz-" + businessId + "-kyc-" + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        Files.write(target, file.getBytes());
        return "/uploads/verification/" + filename;
    }
}
