package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.catalog.model.Category;
import com.duemet.catalog.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @Data
    public static class CategoryRequest {
        @NotBlank
        private String name;
        private String slug;
        private String icon;
        private String imageUrl;
        private String searchTags;
        private Long parentId;
        private Boolean active;
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> createCategory(@Valid @RequestBody CategoryRequest request) {
        Category parent = null;
        if (request.getParentId() != null) {
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found."));
        }

        String imageUrl = blankToNull(request.getImageUrl());
        Category category = Category.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .icon(request.getIcon())
                .imageUrl(imageUrl)
                .searchTags(request.getSearchTags())
                .parent(parent)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        categoryRepository.save(category);
        return ResponseEntity.ok(new MessageResponse("Category created successfully!"));
    }

    @PutMapping("/admin/categories/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));

        Category parent = null;
        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Category cannot be its own parent."));
            }
            parent = categoryRepository.findById(request.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found."));
        }

        category.setName(request.getName());
        category.setIcon(request.getIcon());
        category.setImageUrl(blankToNull(request.getImageUrl()));
        category.setSearchTags(request.getSearchTags());
        category.setParent(parent);
        if (request.getActive() != null) {
            category.setActive(request.getActive());
        }
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            category.setSlug(request.getSlug());
        }

        categoryRepository.save(category);
        return ResponseEntity.ok(new MessageResponse("Category updated successfully!"));
    }

    @DeleteMapping("/admin/categories/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deleteCategory(@PathVariable Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found."));
        
        category.setActive(false);
        categoryRepository.save(category);
        return ResponseEntity.ok(new MessageResponse("Category deactivated successfully."));
    }

    @GetMapping("/public/categories")
    public ResponseEntity<List<Category>> getCategoriesHierarchy() {
        return ResponseEntity.ok(categoryRepository.findActiveHierarchy());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
