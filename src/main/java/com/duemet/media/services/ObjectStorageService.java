package com.duemet.media.services;

import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

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

    /** KYC / verification documents. */
    @Value("${app.r2.folders.verification:verification}")
    private String verificationFolder;

    /** Business / provider profile & gallery images. */
    @Value("${app.r2.folders.profile:profile}")
    private String profileFolder;

    /** Marketplace category cover images. */
    @Value("${app.r2.folders.categories:categories}")
    private String categoriesFolder;

    /** Customer service-request attachments. */
    @Value("${app.r2.folders.request-media:request_media}")
    private String requestMediaFolder;

    @Autowired
    private Environment environment;

    /** Production must not silently fall back to local disk for user media and KYC documents. */
    @PostConstruct
    void requireStorageInProd() {
        if (environment.acceptsProfiles(Profiles.of("prod")) && !isConfigured()) {
            throw new IllegalStateException(
                    "Object storage (R2_ENDPOINT, R2_ACCESS_KEY, R2_SECRET_KEY, R2_BUCKET_NAME, R2_PUBLIC_URL) "
                            + "must be configured when the 'prod' profile is active");
        }
    }

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

    public String profileFolder() {
        return normalizeFolder(profileFolder);
    }

    /** @deprecated use {@link #profileFolder()} */
    @Deprecated
    public String businessProfileFolder() {
        return profileFolder();
    }

    public String categoriesFolder() {
        return normalizeFolder(categoriesFolder);
    }

    public String requestMediaFolder() {
        return normalizeFolder(requestMediaFolder);
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
        } catch (S3Exception e) {
            throw translateS3Failure("upload", e);
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

    /** Read an object's bytes by key — used for private files such as verification documents. */
    public byte[] download(String key) {
        if (!isConfigured() || key == null || key.isBlank()) {
            throw new IllegalStateException("Object storage is not configured");
        }
        try (S3Client client = buildClient()) {
            return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                    .asByteArray();
        } catch (S3Exception e) {
            throw translateS3Failure("download", e);
        }
    }

    private RuntimeException translateS3Failure(String action, S3Exception e) {
        int status = e.statusCode();
        log.error("R2 {} failed status={} bucket={} code={} message={}",
                action, status, bucketName, e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null,
                e.getMessage());
        if (status == 403) {
            return new IllegalStateException(
                    "Cloudflare R2 access denied for bucket '" + bucketName + "'. "
                            + "Create a new R2 API token with Object Read & Write on this bucket, "
                            + "then update R2_ACCESS_KEY / R2_SECRET_KEY in .env and restart.",
                    e);
        }
        if (status == 404) {
            return new IllegalStateException(
                    "Cloudflare R2 bucket '" + bucketName + "' was not found. Check R2_BUCKET_NAME.",
                    e);
        }
        return new IllegalStateException("Cloudflare R2 " + action + " failed: " + e.getMessage(), e);
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
