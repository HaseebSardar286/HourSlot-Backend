package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessVerificationDocument;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.organization.services.TenancyService;
import com.duemet.organization.services.VerificationDocumentService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/business/verification-documents")
@PreAuthorize("hasRole('BUSINESS_OWNER')")
public class VerificationDocumentController {

    private final UserRepository userRepository;
    private final TenancyService tenancyService;
    private final VerificationDocumentService verificationDocumentService;

    public VerificationDocumentController(
            UserRepository userRepository,
            TenancyService tenancyService,
            VerificationDocumentService verificationDocumentService) {
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.verificationDocumentService = verificationDocumentService;
    }

    @GetMapping("/types")
    public ResponseEntity<?> types() {
        return ResponseEntity.ok(verificationDocumentService.requiredTypeCatalog());
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = businessFor(userDetails);
        List<Map<String, Object>> docs = verificationDocumentService.list(business).stream()
                .map(this::toView)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documents", docs);
        body.put("readiness", verificationDocumentService.readiness(business));
        List<Map<String, Object>> tier1 = verificationDocumentService.tier1CatalogFor(business);
        List<Map<String, Object>> tier2 = verificationDocumentService.tier2Catalog();
        List<Map<String, Object>> all = new java.util.ArrayList<>(tier1);
        all.addAll(tier2);
        body.put("requiredTypes", all);
        body.put("tier1Types", tier1);
        body.put("tier2Types", tier2);
        return ResponseEntity.ok(body);
    }

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> upload(
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        Business business = businessFor(userDetails);
        BusinessVerificationDocument saved = verificationDocumentService.upload(business, documentType, file);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("document", toView(saved));
        body.put("readiness", verificationDocumentService.readiness(business));
        body.put("message", "Document uploaded for review.");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) throws Exception {
        Business business = businessFor(userDetails);
        BusinessVerificationDocument doc = verificationDocumentService.list(business).stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Document not found."));
        return fileResponse(verificationDocumentService.loadFile(doc));
    }

    static ResponseEntity<byte[]> fileResponse(VerificationDocumentService.DocumentFile file) {
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(file.mimeType()))
                .header("Content-Disposition", org.springframework.http.ContentDisposition.inline()
                        .filename(file.filename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .body(file.bytes());
    }

    private Business businessFor(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return tenancyService.requireBusinessForUser(user);
    }

    private Map<String, Object> toView(BusinessVerificationDocument doc) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", doc.getId());
        view.put("documentType", doc.getDocumentType());
        view.put("label", VerificationDocumentService.labelFor(doc.getDocumentType()));
        view.put("hint", VerificationDocumentService.hintFor(doc.getDocumentType()));
        view.put("tier", VerificationDocumentService.tierFor(doc.getDocumentType()));
        view.put("status", doc.getStatus());
        view.put("originalFilename", doc.getOriginalFilename());
        view.put("url", doc.getMediaAsset() != null ? VerificationDocumentService.ownerFileUrl(doc) : null);
        view.put("reviewNotes", doc.getReviewNotes());
        view.put("reviewedAt", doc.getReviewedAt());
        view.put("createdAt", doc.getCreatedAt());
        return view;
    }
}
