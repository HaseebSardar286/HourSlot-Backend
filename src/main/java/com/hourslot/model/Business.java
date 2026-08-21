package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
public class Business {

    private Long id;

    private Organization organization;

    @NotBlank
    private String name;

    private String slug;

    private String description;

    @Builder.Default
    private BusinessStatus status = BusinessStatus.PENDING;

    private boolean verified;

    private String rejectionReason;

    private String registrationNumber;

    private Category primaryCategory;

    @JsonIgnoreProperties("subcategories")
    @Builder.Default
    private List<Category> secondaryCategories = new ArrayList<>();

    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Builder.Default
    private int ratingCount = 0;

    private String timezone;

    private String locale;

    private Map<String, Object> settings;

    private String logoUrl;

    private String galleryUrls;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @JsonIgnore
    private LocalDateTime deletedAt;

    /** API-only: populated by TenancyService for existing admin UI. */
    private User owner;

    @JsonProperty("commissionRate")
    public double getCommissionRate() {
        return 0.0;
    }

    @JsonProperty("category")
    public String getCategory() {
        return primaryCategory == null ? null : primaryCategory.getName();
    }

    @JsonProperty("rating")
    public double getRating() {
        return ratingAvg == null ? 0.0 : ratingAvg.doubleValue();
    }

    public void setRating(double rating) {
        this.ratingAvg = BigDecimal.valueOf(rating);
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.verified = false;
        if (this.status == null) {
            this.status = BusinessStatus.PENDING;
        }
        if (this.ratingAvg == null) {
            this.ratingAvg = BigDecimal.ZERO;
        }
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = slugify(this.name);
        }
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
