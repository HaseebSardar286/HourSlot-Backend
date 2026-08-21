package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BusinessVerificationDocument {

    public static final String TRADE_LICENSE = "TRADE_LICENSE";
    public static final String BANK_STATEMENT = "BANK_STATEMENT";
    public static final String OWNER_GOVERNMENT_ID = "OWNER_GOVERNMENT_ID";

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private Long id;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization", "secondaryCategories"})
    private Business business;

    private String documentType;

    private MediaAsset mediaAsset;

    private String originalFilename;

    @Builder.Default
    private String status = STATUS_SUBMITTED;

    private String reviewNotes;

    @JsonIgnoreProperties({"passwordHash", "hibernateLazyInitializer", "handler"})
    private User reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = STATUS_SUBMITTED;
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
