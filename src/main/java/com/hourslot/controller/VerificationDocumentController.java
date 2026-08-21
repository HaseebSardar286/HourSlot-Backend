package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.Business;
import com.hourslot.model.BusinessVerificationDocument;
import com.hourslot.model.User;
import com.hourslot.repository.UserRepository;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.TenancyService;
import com.hourslot.service.VerificationDocumentService;
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
        body.put("requiredTypes", verificationDocumentService.requiredTypeCatalog());
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

    private Business businessFor(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return tenancyService.requireBusinessForUser(user);
    }

    private Map<String, Object> toView(BusinessVerificationDocument doc) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", doc.getId());
        view.put("documentType", doc.getDocumentType());
        view.put("label", VerificationDocumentService.labelFor(doc.getDocumentType()));
        view.put("status", doc.getStatus());
        view.put("originalFilename", doc.getOriginalFilename());
        view.put("url", doc.getMediaAsset() != null ? doc.getMediaAsset().getUrl() : null);
        view.put("reviewNotes", doc.getReviewNotes());
        view.put("reviewedAt", doc.getReviewedAt());
        view.put("createdAt", doc.getCreatedAt());
        return view;
    }
}
