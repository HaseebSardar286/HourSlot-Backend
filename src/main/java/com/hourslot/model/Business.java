package com.hourslot.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "businesses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"password", "hibernateLazyInitializer", "handler"})
    private User owner;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_category_id")
    private Category primaryCategory;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "business_secondary_categories",
        joinColumns = @JoinColumn(name = "business_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @JsonIgnoreProperties("subcategories")
    private java.util.List<Category> secondaryCategories;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BusinessStatus status = BusinessStatus.PENDING;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "commission_rate", nullable = false)
    private double commissionRate;

    private double rating;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "slug", unique = true)
    private String slug;

    @Column(name = "registration_number")
    private String registrationNumber;

    @Column(name = "gallery_urls", columnDefinition = "TEXT")
    private String galleryUrls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.verified = false;
        this.commissionRate = 10.0;
        if (this.status == null) {
            this.status = BusinessStatus.PENDING;
        }
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = slugify(this.name);
        }
    }

    private String slugify(String text) {
        if (text == null || text.isBlank()) return "";
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
