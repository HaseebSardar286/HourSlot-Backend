package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.User;
import com.hourslot.repository.UserRepository;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.RbacService;
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
}
