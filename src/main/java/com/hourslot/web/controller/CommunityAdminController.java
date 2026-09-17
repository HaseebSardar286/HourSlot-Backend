package com.hourslot.web.controller;

import com.hourslot.community.services.CommunityService;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/community")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class CommunityAdminController {
    private final CommunityService communityService;
    private final UserRepository userRepository;

    public CommunityAdminController(CommunityService communityService, UserRepository userRepository) {
        this.communityService = communityService;
        this.userRepository = userRepository;
    }

    @Data
    public static class ReviewBody {
        private String status;
        private String action;
    }

    @GetMapping("/reports")
    public ResponseEntity<?> pendingReports() {
        return ResponseEntity.ok(communityService.pendingReports());
    }

    @PostMapping("/reports/{id}/review")
    public ResponseEntity<?> review(
            @PathVariable Long id,
            @RequestBody ReviewBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User admin = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity.ok(communityService.reviewReport(
                admin, id, body.getStatus(), body.getAction()));
    }
}
