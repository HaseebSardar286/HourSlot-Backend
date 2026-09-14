package com.hourslot.organization.services;

import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessVerificationDocument;
import com.hourslot.media.model.MediaAsset;
import com.hourslot.identity.model.User;
import com.hourslot.organization.repository.BusinessVerificationDocumentRepository;
import com.hourslot.media.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.hourslot.media.services.ObjectStorageService;

@Service
public class VerificationDocumentService {

    /** Tier 1 — unlocks listing (Approve) after admin review. */
    public static final List<String> TIER1_TYPES = List.of(
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID,
            BusinessVerificationDocument.TRADE_LICENSE,
            BusinessVerificationDocument.BUSINESS_ADDRESS_PROOF
    );

    /** Tier 2 — unlocks Verified badge after admin review. */
    public static final List<String> TIER2_TYPES = List.of(
            BusinessVerificationDocument.TAX_ID,
            BusinessVerificationDocument.BANK_STATEMENT
    );

    public static final Set<String> ALL_TYPES;

    static {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(TIER1_TYPES);
        all.addAll(TIER2_TYPES);
        ALL_TYPES = Set.copyOf(all);
    }

    private static final Map<String, String> TYPE_LABELS = Map.of(
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID, "Owner government ID",
            BusinessVerificationDocument.TRADE_LICENSE, "Trade / business license",
            BusinessVerificationDocument.BUSINESS_ADDRESS_PROOF,
            "Address proof (utility bill/lease or shop photos)",
            BusinessVerificationDocument.TAX_ID, "Tax ID / NTN / VAT certificate",
            BusinessVerificationDocument.BANK_STATEMENT, "Bank statement"
    );

    private static final Map<String, String> TYPE_HINTS = Map.of(
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID,
            "National ID, passport, or driver’s license of the account owner.",
            BusinessVerificationDocument.TRADE_LICENSE,
            "Trade license, business registration, or shop permit matching your listing name.",
            BusinessVerificationDocument.BUSINESS_ADDRESS_PROOF,
            "Prove where you operate — upload a recent utility bill/lease or clear photos of your shop front.",
            BusinessVerificationDocument.TAX_ID,
            "Tax registration / NTN / VAT / EIN certificate. Entity name should match the business.",
            BusinessVerificationDocument.BANK_STATEMENT,
            "Recent bank statement or bank letter in the business/owner name. You may redact amounts."
    );

    private static final Map<String, Integer> TYPE_TIER = Map.of(
            BusinessVerificationDocument.OWNER_GOVERNMENT_ID, 1,
            BusinessVerificationDocument.TRADE_LICENSE, 1,
            BusinessVerificationDocument.BUSINESS_ADDRESS_PROOF, 1,
            BusinessVerificationDocument.TAX_ID, 2,
            BusinessVerificationDocument.BANK_STATEMENT, 2
    );

    private final BusinessVerificationDocumentRepository documentRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final String uploadDir;

    public VerificationDocumentService(
            BusinessVerificationDocumentRepository documentRepository,
            MediaAssetRepository mediaAssetRepository,
            ObjectStorageService objectStorageService,
            @org.springframework.beans.factory.annotation.Value("${app.upload.dir:uploads}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.objectStorageService = objectStorageService;
        this.uploadDir = uploadDir;
    }

    public static String labelFor(String type) {
        return TYPE_LABELS.getOrDefault(type, type);
    }

    public static String hintFor(String type) {
        return TYPE_HINTS.getOrDefault(type, "");
    }

    public static int tierFor(String type) {
        return TYPE_TIER.getOrDefault(type, 0);
    }

    public List<Map<String, Object>> typeCatalog(List<String> types) {
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (String code : types) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("label", labelFor(code));
            row.put("hint", hintFor(code));
            row.put("tier", tierFor(code));
            catalog.add(row);
        }
        return catalog;
    }

    /** All uploadable types (tier 1 + tier 2). */
    public List<Map<String, Object>> requiredTypeCatalog() {
        List<String> ordered = new ArrayList<>();
        ordered.addAll(TIER1_TYPES);
        ordered.addAll(TIER2_TYPES);
        return typeCatalog(ordered);
    }

    public List<Map<String, Object>> tier1Catalog() {
        return typeCatalog(TIER1_TYPES);
    }

    public List<Map<String, Object>> tier2Catalog() {
        return typeCatalog(TIER2_TYPES);
    }

    @Transactional(readOnly = true)
    public List<BusinessVerificationDocument> list(Business business) {
        return documentRepository.findByBusinessOrderByCreatedAtDesc(business);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readiness(Business business) {
        List<BusinessVerificationDocument> docs = list(business);
        Map<String, BusinessVerificationDocument> byType = latestByType(docs);

        long tier1Approved = countApproved(TIER1_TYPES, byType);
        long tier2Approved = countApproved(TIER2_TYPES, byType);
        boolean readyForListing = tier1Approved == TIER1_TYPES.size();
        boolean readyForVerifiedBadge = tier2Approved == TIER2_TYPES.size();

        Map<String, Object> result = new LinkedHashMap<>();
        long tier1Submitted = TIER1_TYPES.stream().filter(byType::containsKey).count();
        long tier2Submitted = TIER2_TYPES.stream().filter(byType::containsKey).count();

        result.put("tier1RequiredCount", TIER1_TYPES.size());
        result.put("tier1ApprovedCount", tier1Approved);
        result.put("tier1SubmittedCount", tier1Submitted);
        result.put("tier2RequiredCount", TIER2_TYPES.size());
        result.put("tier2ApprovedCount", tier2Approved);
        result.put("tier2SubmittedCount", tier2Submitted);
        result.put("requiredCount", TIER1_TYPES.size() + TIER2_TYPES.size());
        result.put("approvedCount", tier1Approved + tier2Approved);
        result.put("submittedCount", byType.size());
        result.put("readyForListing", readyForListing);
        result.put("readyForVerifiedBadge", readyForVerifiedBadge);
        result.put("missingTier1Types", missingApproved(TIER1_TYPES, byType));
        result.put("missingTier2Types", missingApproved(TIER2_TYPES, byType));
        result.put("missingTypes", missingApproved(
                List.of(TIER1_TYPES.get(0), TIER1_TYPES.get(1), TIER1_TYPES.get(2),
                        TIER2_TYPES.get(0), TIER2_TYPES.get(1)),
                byType));
        result.put("tier1Types", tier1Catalog());
        result.put("tier2Types", tier2Catalog());
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
                MediaAsset old = existing.getMediaAsset();
                if (objectStorageService.isConfigured()) {
                    if (old.getStorageKey() != null && old.getStorageKey().contains("/")) {
                        objectStorageService.deleteByKey(old.getStorageKey());
                    } else {
                        objectStorageService.deleteByPublicUrl(old.getUrl());
                    }
                }
                old.setDeletedAt(LocalDateTime.now());
                mediaAssetRepository.save(old);
            }
        });

        StoredFile stored = storeFile(business.getId(), type, file);
        MediaAsset asset = mediaAssetRepository.save(MediaAsset.builder()
                .ownerType("BUSINESS")
                .ownerId(business.getId())
                .storageKey(stored.storageKey())
                .url(stored.url())
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
    public void requireReadyForListing(Business business) {
        Map<String, Object> readiness = readiness(business);
        if (!Boolean.TRUE.equals(readiness.get("readyForListing"))) {
            throw new IllegalStateException(
                    "Listing approval requires approved Owner ID, Trade license, and Address proof.");
        }
    }

    @Transactional(readOnly = true)
    public void requireReadyForVerifiedBadge(Business business) {
        Map<String, Object> readiness = readiness(business);
        if (!Boolean.TRUE.equals(readiness.get("readyForVerifiedBadge"))) {
            throw new IllegalStateException(
                    "Verified badge requires approved Tax ID and Bank statement.");
        }
    }

    private Map<String, BusinessVerificationDocument> latestByType(List<BusinessVerificationDocument> docs) {
        Map<String, BusinessVerificationDocument> byType = new LinkedHashMap<>();
        for (BusinessVerificationDocument doc : docs) {
            byType.putIfAbsent(doc.getDocumentType(), doc);
        }
        return byType;
    }

    private long countApproved(List<String> types, Map<String, BusinessVerificationDocument> byType) {
        return types.stream()
                .filter(t -> byType.containsKey(t)
                        && BusinessVerificationDocument.STATUS_APPROVED.equals(byType.get(t).getStatus()))
                .count();
    }

    private List<Map<String, Object>> missingApproved(
            List<String> types,
            Map<String, BusinessVerificationDocument> byType) {
        return types.stream()
                .filter(t -> !byType.containsKey(t)
                        || !BusinessVerificationDocument.STATUS_APPROVED.equals(byType.get(t).getStatus()))
                .map(t -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", t);
                    row.put("label", labelFor(t));
                    row.put("hint", hintFor(t));
                    row.put("tier", tierFor(t));
                    return row;
                })
                .toList();
    }

    private String normalizeType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            throw new IllegalArgumentException("Document type is required.");
        }
        String type = documentType.trim().toUpperCase(Locale.ROOT);
        if (!ALL_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Document type must be one of: OWNER_GOVERNMENT_ID, TRADE_LICENSE, BUSINESS_ADDRESS_PROOF, TAX_ID, BANK_STATEMENT.");
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

    private StoredFile storeFile(Long businessId, String documentType, MultipartFile file) throws IOException {
        String ext = resolveExtension(file);
        String namePrefix = "biz-" + businessId + "-" + documentType.toLowerCase(Locale.ROOT);

        if (objectStorageService.isConfigured()) {
            ObjectStorageService.StoredObject stored = objectStorageService.upload(
                    objectStorageService.verificationFolder(),
                    namePrefix,
                    file.getContentType(),
                    file.getBytes(),
                    ext);
            return new StoredFile(stored.storageKey(), stored.publicUrl());
        }

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("verification");
        Files.createDirectories(dir);
        String filename = namePrefix + "-" + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        Files.write(target, file.getBytes());
        return new StoredFile(filename, "/uploads/verification/" + filename);
    }

    private static String resolveExtension(MultipartFile file) {
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
            } else if (ct.contains("jpeg") || ct.contains("jpg")) {
                ext = ".jpg";
            } else {
                ext = ".bin";
            }
        }
        return ext;
    }

    private record StoredFile(String storageKey, String url) {}
}
