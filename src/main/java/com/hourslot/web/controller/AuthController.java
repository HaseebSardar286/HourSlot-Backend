package com.hourslot.web.controller;

import com.hourslot.identity.model.AuthRefreshToken;
import com.hourslot.identity.repository.AuthRefreshTokenRepository;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.model.BusinessStatus;
import com.hourslot.catalog.model.Category;
import com.hourslot.catalog.repository.CategoryRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.identity.model.CustomerProfile;
import com.hourslot.identity.repository.CustomerProfileRepository;
import com.hourslot.identity.model.EmailVerificationToken;
import com.hourslot.identity.repository.EmailVerificationTokenRepository;
import com.hourslot.identity.security.JwtUtils;
import com.hourslot.identity.dto.LoginRequest;
import com.hourslot.identity.dto.LoginResponse;
import com.hourslot.notification.services.MailService;
import com.hourslot.identity.dto.MessageResponse;
import com.hourslot.notification.services.NotificationPreferenceService;
import com.hourslot.organization.model.Organization;
import com.hourslot.identity.model.PasswordResetToken;
import com.hourslot.identity.repository.PasswordResetTokenRepository;
import com.hourslot.organization.services.RbacService;
import com.hourslot.identity.dto.RegisterRequest;
import com.hourslot.organization.model.Staff;
import com.hourslot.organization.services.StaffInviteService;
import com.hourslot.organization.services.TenancyService;
import com.hourslot.shared.util.TokenHashes;
import com.hourslot.identity.dto.TokenRefreshRequest;
import com.hourslot.identity.dto.TokenRefreshResponse;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.model.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private NotificationPreferenceService notificationPreferenceService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private StaffInviteService staffInviteService;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private MailService mailService;

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;
        @NotBlank
        @Size(min = 6)
        private String newPassword;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login request initiated for user: {}", loginRequest.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateAccessToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Retrieve extra metadata from User entity for the response
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        persistRefreshToken(user, refreshToken);

        return ResponseEntity.ok(new LoginResponse(
                jwt,
                refreshToken,
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getRole().name(),
                user.getFirstName(),
                user.getLastName(),
                roles));
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Only allow self-registration as CUSTOMER or BUSINESS_OWNER
        UserRole userRole = UserRole.CUSTOMER;
        if (signUpRequest.getRole() != null) {
            try {
                userRole = UserRole.valueOf(signUpRequest.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Invalid role specified."));
            }
            if (userRole != UserRole.CUSTOMER && userRole != UserRole.BUSINESS_OWNER) {
                return ResponseEntity
                        .badRequest()
                        .body(new MessageResponse("Error: Registration is limited to CUSTOMER or BUSINESS_OWNER roles."));
            }
        }

        // Create new user's account
        User user = User.builder()
                .email(signUpRequest.getEmail())
                .passwordHash(encoder.encode(signUpRequest.getPassword()))
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        if (userRole == UserRole.CUSTOMER) {
            customerProfileRepository.save(CustomerProfile.builder()
                    .user(savedUser)
                    .build());
            rbacService.grantSystemRole(savedUser, "CUSTOMER", null, null, null, null);
        }

        if (userRole == UserRole.BUSINESS_OWNER) {
            String businessName = signUpRequest.getBusinessName();
            if (businessName == null || businessName.isBlank()) {
                businessName = savedUser.getFirstName() + "'s Business";
            }

            Category primaryCategory = null;
            if (signUpRequest.getBusinessCategory() != null && !signUpRequest.getBusinessCategory().isBlank()) {
                String searchSlug = signUpRequest.getBusinessCategory().toLowerCase()
                        .replaceAll("[^a-z0-9\\s-]", "")
                        .replaceAll("\\s+", "-")
                        .trim();
                primaryCategory = categoryRepository.findBySlug(searchSlug).orElse(null);
                if (primaryCategory == null) {
                    primaryCategory = categoryRepository.findByActiveTrue().stream()
                            .filter(c -> c.getName().equalsIgnoreCase(signUpRequest.getBusinessCategory()))
                            .findFirst()
                            .orElse(null);
                }
            }

            Organization organization = tenancyService.provisionOrganization(savedUser, businessName);
            Business business = Business.builder()
                    .name(businessName)
                    .organization(organization)
                    .description(signUpRequest.getBusinessDescription())
                    .registrationNumber(signUpRequest.getRegistrationNumber())
                    .primaryCategory(primaryCategory)
                    .status(BusinessStatus.PENDING)
                    .build();
            businessRepository.save(business);
        }

        notificationPreferenceService.ensureDefaults(savedUser);
        issueEmailVerification(savedUser);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    @GetMapping("/verify-email")
    @Transactional
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByTokenHash(TokenHashes.sha256(token))
                .orElse(null);
        if (verificationToken == null || verificationToken.isUsed() || verificationToken.isExpired()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Verification link is invalid or expired."));
        }
        User user = verificationToken.getUser();
        user.setEmailVerifiedAt(LocalDateTime.now());
        userRepository.save(user);
        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully."));
    }

    private void issueEmailVerification(User user) {
        emailVerificationTokenRepository.deleteByUser(user);
        String token = UUID.randomUUID().toString();
        emailVerificationTokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(TokenHashes.sha256(token))
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .used(false)
                .build());
        mailService.sendEmailVerificationEmail(user.getEmail(), user.getFirstName(), token);
        log.info("Email verification sent for {}", user.getEmail());
    }

    @GetMapping("/staff-invite")
    public ResponseEntity<?> previewStaffInvite(@RequestParam String token) {
        return ResponseEntity.ok(staffInviteService.preview(token));
    }

    @Data
    public static class AcceptStaffInviteRequest {
        @NotBlank
        private String token;
        private String firstName;
        private String lastName;
        @NotBlank
        @Size(min = 6, max = 40)
        private String password;
        private String phoneNumber;
    }

    @PostMapping("/staff-invite/accept")
    public ResponseEntity<?> acceptStaffInvite(@Valid @RequestBody AcceptStaffInviteRequest request) {
        Staff staff = staffInviteService.accept(
                request.getToken(),
                request.getFirstName(),
                request.getLastName(),
                request.getPassword(),
                request.getPhoneNumber());
        return ResponseEntity.ok(Map.of(
                "message", "Invite accepted. You can sign in with your email.",
                "staffId", staff.getId(),
                "email", staff.getUser() != null ? staff.getUser().getEmail() : null
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        if (jwtUtils.validateJwtToken(requestRefreshToken) && jwtUtils.isRefreshToken(requestRefreshToken)) {
            String username = jwtUtils.getUsernameFromJwtToken(requestRefreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails,
                    null, userDetails.getAuthorities());

            String token = jwtUtils.generateAccessToken(authentication);
            String newRefreshToken = jwtUtils.generateRefreshToken(authentication);
            if (userDetails instanceof CustomUserDetails custom) {
                userRepository.findById(custom.getId()).ifPresent(user -> persistRefreshToken(user, newRefreshToken));
            }

            return ResponseEntity.ok(new TokenRefreshResponse(token, newRefreshToken));
        }

        return ResponseEntity.badRequest().body(new MessageResponse("Refresh token is expired or invalid!"));
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Always return the same message to avoid email enumeration. Never log or return the raw token.
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser(user);
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .tokenHash(TokenHashes.sha256(token))
                    .user(user)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(false)
                    .build();
            passwordResetTokenRepository.save(resetToken);
            mailService.sendPasswordResetEmail(user.getEmail(), token);
            log.info("Password reset email sent for {}", user.getEmail());
        });

        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that email, a password reset link has been generated."));
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(TokenHashes.sha256(request.getToken()))
                .orElse(null);
        if (resetToken == null || resetToken.isUsed() || resetToken.isExpired()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Reset token is invalid or expired."));
        }
        User user = resetToken.getUser();
        user.setPassword(encoder.encode(request.getNewPassword()));
        userRepository.save(user);
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        return ResponseEntity.ok(new MessageResponse("Password has been reset successfully."));
    }

    private void persistRefreshToken(User user, String refreshToken) {
        authRefreshTokenRepository.save(AuthRefreshToken.builder()
                .user(user)
                .tokenHash(TokenHashes.sha256(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build());
    }
}
