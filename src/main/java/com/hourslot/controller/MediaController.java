package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.Business;
import com.hourslot.model.User;
import com.hourslot.repository.BusinessRepository;
import com.hourslot.repository.UserRepository;
import com.hourslot.security.CustomUserDetails;
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
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/business/media")
public class MediaController {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

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
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Only image uploads are allowed."));
        }

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        String ext = contentType.contains("png") ? ".png"
                : contentType.contains("webp") ? ".webp"
                : contentType.contains("gif") ? ".gif" : ".jpg";
        String filename = "biz-" + business.getId() + "-" + UUID.randomUUID() + ext;
        Path target = dir.resolve(filename);
        Files.write(target, file.getBytes());

        String publicUrl = "/uploads/" + filename;
        String existing = business.getGalleryUrls();
        if (existing == null || existing.isBlank()) {
            business.setGalleryUrls(publicUrl);
        } else {
            business.setGalleryUrls(existing + "," + publicUrl);
        }
        businessRepository.save(business);

        return ResponseEntity.ok(java.util.Map.of(
                "url", publicUrl,
                "galleryUrls", business.getGalleryUrls()
        ));
    }

    @DeleteMapping("/gallery")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> removeGalleryUrl(
            @RequestParam String url,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (business.getGalleryUrls() == null) {
            return ResponseEntity.ok(new MessageResponse("Gallery already empty."));
        }
        String updated = Arrays.stream(business.getGalleryUrls().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.equals(url))
                .collect(Collectors.joining(","));
        business.setGalleryUrls(updated.isBlank() ? null : updated);
        businessRepository.save(business);
        return ResponseEntity.ok(new MessageResponse("Image removed from gallery."));
    }
}
