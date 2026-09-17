package com.hourslot.web.controller;

import com.hourslot.community.services.CommunityService;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.services.FeatureFlagService;
import com.hourslot.organization.services.TenancyService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class CommunityController {
    private final CommunityService communityService;
    private final UserRepository userRepository;
    private final FeatureFlagService featureFlagService;
    private final TenancyService tenancyService;

    public CommunityController(
            CommunityService communityService,
            UserRepository userRepository,
            FeatureFlagService featureFlagService,
            TenancyService tenancyService) {
        this.communityService = communityService;
        this.userRepository = userRepository;
        this.featureFlagService = featureFlagService;
        this.tenancyService = tenancyService;
    }

    @Data
    public static class PostBody {
        private String category;
        private String title;
        private String body;
        private Long providerBusinessId;
    }

    @Data
    public static class CommentBody {
        private String body;
    }

    @Data
    public static class ReactionBody {
        private String reactionType;
    }

    @Data
    public static class ReportBody {
        private String reason;
        private String description;
    }

    @GetMapping("/api/communities")
    public ResponseEntity<?> communities() {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.listCommunities());
    }

    @PostMapping("/api/communities/{id}/join")
    public ResponseEntity<?> join(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.join(user(userDetails), id));
    }

    @PostMapping("/api/communities/{id}/leave")
    public ResponseEntity<?> leave(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.leave(user(userDetails), id));
    }

    @GetMapping("/api/communities/{id}/posts")
    public ResponseEntity<?> communityPosts(@PathVariable Long id) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.postsForCommunity(id));
    }

    @PostMapping("/api/communities/{id}/posts")
    public ResponseEntity<?> createPost(
            @PathVariable Long id,
            @RequestBody PostBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        User user = user(userDetails);
        Long providerBusinessId = body.getProviderBusinessId();
        if (providerBusinessId == null) {
            try {
                Business business = tenancyService.requireBusinessForUser(user);
                providerBusinessId = business.getId();
            } catch (RuntimeException ignored) {
                // customer post without provider reference
            }
        }
        return ResponseEntity.ok(communityService.createPost(
                user, id, body.getCategory(), body.getTitle(), body.getBody(), providerBusinessId));
    }

    @GetMapping("/api/community/posts/{id}")
    public ResponseEntity<?> postDetail(@PathVariable Long id) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.postDetail(id));
    }

    @PostMapping("/api/community/posts/{id}/comments")
    public ResponseEntity<?> comment(
            @PathVariable Long id,
            @RequestBody CommentBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.addComment(user(userDetails), id, body.getBody()));
    }

    @PostMapping("/api/community/posts/{id}/reactions")
    public ResponseEntity<?> react(
            @PathVariable Long id,
            @RequestBody(required = false) ReactionBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        String type = body == null ? "LIKE" : body.getReactionType();
        return ResponseEntity.ok(communityService.react(user(userDetails), id, type));
    }

    @PostMapping("/api/community/posts/{id}/report")
    public ResponseEntity<?> reportPost(
            @PathVariable Long id,
            @RequestBody ReportBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.reportPost(
                user(userDetails), id, body.getReason(), body.getDescription()));
    }

    @PostMapping("/api/community/comments/{id}/report")
    public ResponseEntity<?> reportComment(
            @PathVariable Long id,
            @RequestBody ReportBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireCommunityEnabled();
        return ResponseEntity.ok(communityService.reportComment(
                user(userDetails), id, body.getReason(), body.getDescription()));
    }

    private void requireCommunityEnabled() {
        if (!featureFlagService.isEnabled("community")) {
            throw new IllegalStateException("Community feature is not enabled.");
        }
    }

    private User user(CustomUserDetails details) {
        return userRepository.findById(details.getId()).orElseThrow();
    }
}
