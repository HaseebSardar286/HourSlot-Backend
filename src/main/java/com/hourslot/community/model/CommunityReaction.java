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
public class CommunityReaction {
    private Long id;
    private Long postId;
    private Long userId;
    @Builder.Default
    private String reactionType = "LIKE";
    private LocalDateTime createdAt;
}
