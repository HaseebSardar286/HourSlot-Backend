package com.hourslot.community.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityPost {
    private Long id;
    private Long communityId;
    private Long authorUserId;
    private Long providerBusinessId;
    @Builder.Default
    private String category = "GENERAL";
    private String title;
    private String body;
    @Builder.Default
    private String status = "PUBLISHED";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (category == null) category = "GENERAL";
        if (status == null) status = "PUBLISHED";
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
