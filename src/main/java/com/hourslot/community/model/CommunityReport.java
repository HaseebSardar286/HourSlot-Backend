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
public class CommunityReport {
    private Long id;
    private Long postId;
    private Long commentId;
    private Long reportedByUserId;
    private String reason;
    private String description;
    @Builder.Default
    private String status = "PENDING";
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "PENDING";
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
