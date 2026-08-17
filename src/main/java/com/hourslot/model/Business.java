package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "businesses")
@SQLRestriction("deleted_at IS NULL")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "organization"})
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BusinessStatus status = BusinessStatus.PENDING;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "registration_number")
    private String registrationNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private Category primaryCategory;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "business_categories",
            joinColumns = @JoinColumn(name = "business_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @JsonIgnoreProperties("subcategories")
    @Builder.Default
    private List<Category> secondaryCategories = new ArrayList<>();

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private int ratingCount = 0;

    private String timezone;

    private String locale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> settings;

    @Formula("(SELECT ma.url FROM business_media bm JOIN media_assets ma ON ma.id = bm.media_asset_id WHERE bm.business_id = id AND bm.role = 'logo' AND ma.deleted_at IS NULL LIMIT 1)")
    private String logoUrl;

    @Formula("(SELECT string_agg(ma.url, ',' ORDER BY ma.sort_order) FROM business_media bm JOIN media_assets ma ON ma.id = bm.media_asset_id WHERE bm.business_id = id AND bm.role = 'gallery' AND ma.deleted_at IS NULL)")
    private String galleryUrls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    @JsonIgnore
    private LocalDateTime deletedAt;

    /** API-only: populated by TenancyService for existing admin UI. */
    @Transient
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

    @PrePersist
    protected void onCreate() {
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

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
