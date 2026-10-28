package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.notification.services.NotificationPreferenceService;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.RbacService;
import com.duemet.organization.services.TenancyService;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationPreferenceService notificationPreferenceService;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Data
    public static class ProfileUpdateRequest {
        private String firstName;
        private String lastName;
        private String phoneNumber;
        @Size(min = 6, message = "Password must be at least 6 characters")
        private String newPassword;
        private String currentPassword;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("email", user.getEmail());
        body.put("firstName", user.getFirstName());
        body.put("lastName", user.getLastName());
        body.put("phoneNumber", user.getPhoneNumber());
        body.put("role", rbacService.resolveAppRole(user.getId()).name());
        body.put("providerIntent", user.getProviderIntent());
        var businessOpt = tenancyService.findBusinessForUser(user);
        body.put("providerWorkspace", businessOpt.isPresent());
        body.put("listingMode", businessOpt.map(b -> b.getListingMode()).orElse(null));
        body.put("providerType", businessOpt.map(b -> b.getProviderType()).orElse(null));
        body.put("onboardingState", businessOpt.map(b -> b.getOnboardingState()).orElse(null));
        body.put("isOnline", businessOpt.map(b -> b.isOnline()).orElse(null));
        body.put("individualDisabled", businessOpt
                .map(b -> b.getIndividualDisabledReason() != null && !b.getIndividualDisabledReason().isBlank())
                .orElse(false));
        body.put("customerWorkspace", true);
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/me")
    public ResponseEntity<?> updateMe(
            @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getCurrentPassword() == null
                    || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                return ResponseEntity.badRequest()
                        .body(new MessageResponse("Current password is incorrect."));
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Profile updated successfully."));
    }

    @GetMapping("/me/notification-preferences")
    public ResponseEntity<?> getNotificationPreferences(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        notificationPreferenceService.ensureDefaults(user);
        return ResponseEntity.ok(notificationPreferenceService.getPreferences(user));
    }

    @PutMapping("/me/notification-preferences")
    public ResponseEntity<?> updateNotificationPreferences(
            @RequestBody Map<String, Boolean> request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return ResponseEntity.ok(notificationPreferenceService.updatePreferences(user, request));
    }
}
