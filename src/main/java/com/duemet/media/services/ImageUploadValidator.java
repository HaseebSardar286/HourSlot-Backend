package com.duemet.media.services;

import java.util.Locale;
import java.util.Set;

/**
 * Shared allowlist for gallery / profile image uploads.
 * Rejects SVG and other scriptable "image" types that browsers may execute.
 */
public final class ImageUploadValidator {

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private ImageUploadValidator() {
    }

    public static boolean isAllowed(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semi = normalized.indexOf(';');
        if (semi > 0) {
            normalized = normalized.substring(0, semi).trim();
        }
        return ALLOWED.contains(normalized);
    }

    public static String extensionFor(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        return ".jpg";
    }

    public static String rejectionMessage() {
        return "Only JPEG, PNG, or WebP images are allowed.";
    }
}
