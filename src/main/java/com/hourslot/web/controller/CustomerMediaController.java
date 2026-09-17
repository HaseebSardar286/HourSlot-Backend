package com.hourslot.web.controller;

import com.hourslot.identity.dto.MessageResponse;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.media.services.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile/media")
public class CustomerMediaController {
    private final ObjectStorageService objectStorageService;
    private final String uploadDir;

    public CustomerMediaController(
            ObjectStorageService objectStorageService,
            @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.objectStorageService = objectStorageService;
        this.uploadDir = uploadDir;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty."));
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Only image uploads are allowed."));
        }
        String ext = contentType.contains("png") ? ".png"
                : contentType.contains("webp") ? ".webp"
                : contentType.contains("gif") ? ".gif" : ".jpg";
        String publicUrl;
        if (objectStorageService.isConfigured()) {
            ObjectStorageService.StoredObject stored = objectStorageService.upload(
                    "request_media",
                    "user-" + userDetails.getId(),
                    contentType,
                    file.getBytes(),
                    ext);
            publicUrl = stored.publicUrl();
        } else {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve("request_media");
            Files.createDirectories(dir);
            String filename = "user-" + userDetails.getId() + "-" + UUID.randomUUID() + ext;
            Files.write(dir.resolve(filename), file.getBytes());
            publicUrl = "/uploads/request_media/" + filename;
        }
        return ResponseEntity.ok(Map.of("url", publicUrl));
    }
}
