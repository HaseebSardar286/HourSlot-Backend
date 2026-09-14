package com.hourslot.media.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

@Service
public class ObjectStorageService {

    private static final Logger log = LogManager.getLogger(ObjectStorageService.class);

    @Value("${app.r2.endpoint:}")
    private String endpoint;

    @Value("${app.r2.region:auto}")
    private String region;

    @Value("${app.r2.access-key:}")
    private String accessKey;

    @Value("${app.r2.secret-key:}")
    private String secretKey;

    @Value("${app.r2.bucket-name:hour-slot}")
    private String bucketName;

    @Value("${app.r2.public-url:}")
    private String publicUrl;

    @Value("${app.r2.path-style-access:true}")
    private boolean pathStyleAccess;

    @Value("${app.r2.chunked-encoding:false}")
    private boolean chunkedEncoding;

    /** Matches the R2 folder created in bucket hour-slot. */
    @Value("${app.r2.folders.verification:verification}")
    private String verificationFolder;

    /** Matches the R2 folder created in bucket hour-slot (spelling as created). */
    @Value("${app.r2.folders.business-profile:bussiness_profile}")
    private String businessProfileFolder;

    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank()
                && accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && publicUrl != null && !publicUrl.isBlank()
                && bucketName != null && !bucketName.isBlank();
    }

    public String verificationFolder() {
        return normalizeFolder(verificationFolder);
    }

    public String businessProfileFolder() {
        return normalizeFolder(businessProfileFolder);
    }

    /**
     * Upload under a bucket folder, e.g. verification/biz-1-kyc-uuid.pdf
     */
    public StoredObject upload(String folder, String namePrefix, String contentType, byte[] bytes, String extension) {
        if (!isConfigured()) {
            throw new IllegalStateException("Object storage is not configured");
        }

        String safeExt = normalizeExtension(extension);
        String key = normalizeFolder(folder) + "/" + sanitizeName(namePrefix) + "-" + UUID.randomUUID() + safeExt;
        try (S3Client client = buildClient()) {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType != null && !contentType.isBlank()
                                    ? contentType
                                    : "application/octet-stream")
                            .build(),
                    RequestBody.fromBytes(bytes));
        }

        String url = publicBase() + "/" + key;
        log.info("Uploaded object to R2 bucket={} key={}", bucketName, key);
        return new StoredObject(key, url);
    }

    public void deleteByPublicUrl(String url) {
        if (!isConfigured() || url == null || url.isBlank()) {
            return;
        }
        String base = publicBase();
        if (!url.startsWith(base + "/")) {
            return;
        }
        String key = url.substring(base.length() + 1);
        deleteByKey(key);
    }

    public void deleteByKey(String key) {
        if (!isConfigured() || key == null || key.isBlank()) {
            return;
        }
        try (S3Client client = buildClient()) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
            log.info("Deleted object from R2 bucket={} key={}", bucketName, key);
        } catch (Exception e) {
            log.warn("Failed to delete R2 object key={}: {}", key, e.getMessage());
        }
    }

    private S3Client buildClient() {
        String regionId = (region == null || region.isBlank()) ? "auto" : region.trim();
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint.trim()))
                .region(Region.of(regionId))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .chunkedEncodingEnabled(chunkedEncoding)
                        .build())
                .build();
    }

    private String publicBase() {
        String normalized = publicUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private static String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "uploads";
        }
        String f = folder.trim();
        while (f.startsWith("/")) {
            f = f.substring(1);
        }
        while (f.endsWith("/")) {
            f = f.substring(0, f.length() - 1);
        }
        return f;
    }

    private static String sanitizeName(String namePrefix) {
        if (namePrefix == null || namePrefix.isBlank()) {
            return "file";
        }
        return namePrefix.trim().replaceAll("[^a-zA-Z0-9._-]+", "-");
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "";
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (!ext.startsWith(".")) {
            ext = "." + ext;
        }
        if (ext.length() > 10) {
            return "";
        }
        return ext;
    }

    public record StoredObject(String storageKey, String publicUrl) {}
}
