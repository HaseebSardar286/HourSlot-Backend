package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.organization.model.Business;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.media.services.MediaAssetService;
import com.duemet.media.services.ObjectStorageService;
import com.duemet.organization.services.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/business/media")
public class MediaController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty."));
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!com.duemet.media.services.ImageUploadValidator.isAllowed(contentType)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    com.duemet.media.services.ImageUploadValidator.rejectionMessage()));
        }

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        String ext = com.duemet.media.services.ImageUploadValidator.extensionFor(contentType);
        String publicUrl;

        if (objectStorageService.isConfigured()) {
            ObjectStorageService.StoredObject stored = objectStorageService.upload(
                    objectStorageService.profileFolder(),
                    "biz-" + business.getId(),
                    contentType,
                    file.getBytes(),
                    ext);
            publicUrl = stored.publicUrl();
        } else {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize()
                    .resolve(objectStorageService.profileFolder());
            Files.createDirectories(dir);
            String filename = "biz-" + business.getId() + "-" + UUID.randomUUID() + ext;
            Path target = dir.resolve(filename);
            Files.write(target, file.getBytes());
            publicUrl = "/uploads/" + objectStorageService.profileFolder() + "/" + filename;
        }

        String galleryUrls = mediaAssetService.appendGalleryUrl(business.getId(), publicUrl);

        return ResponseEntity.ok(java.util.Map.of(
                "url", publicUrl,
                "galleryUrls", galleryUrls
        ));
    }

    @DeleteMapping("/gallery")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> removeGalleryUrl(
            @RequestParam String url,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (objectStorageService.isConfigured() && url != null && url.startsWith("http")) {
            objectStorageService.deleteByPublicUrl(url);
        }
        mediaAssetService.removeGalleryUrl(business.getId(), url);
        return ResponseEntity.ok(new MessageResponse("Image removed from gallery."));
    }
}
