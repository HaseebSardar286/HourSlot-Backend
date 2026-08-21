package com.hourslot.model;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Category {

    private Long id;

    @NotBlank
    private String name;

    private String slug;

    private String icon;

    private String imageUrl;

    private String searchTags;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int sortOrder = 0;

    @JsonIgnoreProperties({"subcategories", "parent"})
    private Category parent;

    @JsonIgnoreProperties("parent")
    private List<Category> subcategories;

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.slug == null || this.slug.isBlank()) {
            this.slug = this.name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim();
        }
    }
}
