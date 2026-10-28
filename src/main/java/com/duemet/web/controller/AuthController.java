package com.duemet.web.controller;

import com.duemet.identity.model.AuthRefreshToken;
import com.duemet.identity.repository.AuthRefreshTokenRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.catalog.model.Category;
import com.duemet.catalog.repository.CategoryRepository;
import com.duemet.identity.security.AuthCookies;
import com.duemet.identity.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.duemet.identity.model.CustomerProfile;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.identity.model.EmailVerificationToken;
import com.duemet.identity.repository.EmailVerificationTokenRepository;
import com.duemet.identity.security.JwtUtils;
import com.duemet.identity.dto.LoginRequest;
import com.duemet.identity.dto.LoginResponse;
import com.duemet.notification.services.MailService;
import com.duemet.identity.dto.MessageResponse;
import com.duemet.notification.services.NotificationPreferenceService;
import com.duemet.organization.model.Organization;
import com.duemet.identity.model.PasswordResetToken;
import com.duemet.identity.repository.PasswordResetTokenRepository;
import com.duemet.organization.model.Branch;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.services.FeatureFlagService;
import com.duemet.organization.services.ProviderListingService;
import com.duemet.organization.services.RbacService;
import com.duemet.identity.dto.RegisterRequest;
import com.duemet.organization.model.Staff;
import com.duemet.organization.model.BusinessVerificationDocument;
import com.duemet.organization.services.StaffInviteService;
import com.duemet.organization.services.TenancyService;
import com.duemet.organization.services.VerificationDocumentService;
import com.duemet.shared.util.TokenHashes;
import com.duemet.identity.dto.TokenRefreshRequest;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.model.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    @Autowired
    private AuthCookies authCookies;

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
    private ProviderListingService providerListingService;

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private BranchRepository branchRepository;

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

    @Autowired
    private com.duemet.identity.services.EmailLoginCodeService emailLoginCodeService;

    @Autowired
    private VerificationDocumentService verificationDocumentService;

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
    public ResponseEntity<?> authenticateUser(
            @Valid @RequestBody LoginRequest loginRequest, HttpServletResponse httpResponse) {
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

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        persistRefreshToken(user, refreshToken);
        authCookies.write(httpResponse, jwt, refreshToken);

        return ResponseEntity.ok(buildLoginResponse(user, roles, userDetails.getRole().name()));
    }

    @Data
    public static class EmailCodeRequest {
        @NotBlank
        private String email;
    }

    @Data
    public static class EmailCodeVerifyRequest {
        @NotBlank
        private String email;
        @NotBlank
        private String code;
    }

    /** Passwordless sign-in, step 1: email a 6-digit code. Same response whether or not the account exists. */
    @PostMapping("/email-code/request")
    public ResponseEntity<?> requestEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        emailLoginCodeService.requestCode(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If the address can receive email, a sign-in code is on its way."));
    }

    /** Passwordless sign-in, step 2: exchange the code for the same tokens a password login returns. */
    @PostMapping("/email-code/verify")
    public ResponseEntity<?> verifyEmailCode(
            @Valid @RequestBody EmailCodeVerifyRequest request, HttpServletResponse httpResponse) {
        User user = emailLoginCodeService.verify(request.getEmail(), request.getCode());
        UserDetails loaded = userDetailsService.loadUserByUsername(user.getEmail());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                loaded, null, loaded.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateAccessToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) loaded;
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        User fresh = userRepository.findById(userDetails.getId()).orElseThrow();
        fresh.setLastLoginAt(LocalDateTime.now());
        userRepository.save(fresh);
        persistRefreshToken(fresh, refreshToken);
        authCookies.write(httpResponse, jwt, refreshToken);
        log.info("Email-code sign-in for userId={}", fresh.getId());
        return ResponseEntity.ok(buildLoginResponse(fresh, roles, userDetails.getRole().name()));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest signUpRequest) {
        return registerInternal(signUpRequest, null, null);
    }

    /**
     * Same as JSON register, plus optional KYC files from the signup form.
     * Does not create a session — the client must sign in afterward.
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> registerUserWithDocuments(
            @RequestPart("data") @Valid RegisterRequest signUpRequest,
            @RequestPart(value = "governmentId", required = false) MultipartFile governmentId,
            @RequestPart(value = "tradeLicense", required = false) MultipartFile tradeLicense) {
        return registerInternal(signUpRequest, governmentId, tradeLicense);
    }

    private ResponseEntity<?> registerInternal(
            RegisterRequest signUpRequest,
            MultipartFile governmentId,
            MultipartFile tradeLicense) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        final String accountType;
        try {
            accountType = resolveAccountType(signUpRequest);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponse(ex.getMessage()));
        }
        UserRole userRole = "BUSINESS".equals(accountType) ? UserRole.BUSINESS_OWNER : UserRole.CUSTOMER;

        User user = User.builder()
                .email(signUpRequest.getEmail())
                .passwordHash(encoder.encode(signUpRequest.getPassword()))
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .status("ACTIVE")
                .providerIntent("INDIVIDUAL".equals(accountType) ? "INDIVIDUAL"
                        : "BUSINESS".equals(accountType) ? "BUSINESS" : "CUSTOMER")
                .build();

        User savedUser = userRepository.save(user);
        Business provisionedListing = null;

        // Dual identity: customers and individuals both get a customer profile
        if (userRole == UserRole.CUSTOMER) {
            customerProfileRepository.save(CustomerProfile.builder()
                    .user(savedUser)
                    .build());
            rbacService.grantSystemRole(savedUser, "CUSTOMER", null, null, null, null);
        }

        if ("INDIVIDUAL".equals(accountType)) {
            if (!featureFlagService.isEnabled("individual_providers")) {
                return ResponseEntity.status(403).body(new MessageResponse(
                        "Individual provider registration is not currently available."));
            }
            String displayName = blankToNull(signUpRequest.getDisplayName());
            if (displayName == null) {
                displayName = ((savedUser.getFirstName() == null ? "" : savedUser.getFirstName()) + " "
                        + (savedUser.getLastName() == null ? "" : savedUser.getLastName())).trim();
            }
            if (displayName.isBlank()) {
                displayName = "Individual Provider";
            }
            Business business = tenancyService.provisionIndividualProvider(
                    savedUser,
                    displayName,
                    signUpRequest.getCountryCode(),
                    signUpRequest.getRegion(),
                    signUpRequest.getCity(),
                    signUpRequest.getTimezone(),
                    signUpRequest.getCurrency());
            String serviceMode = blankToNull(signUpRequest.getServiceMode());
            if (serviceMode == null) {
                serviceMode = "CUSTOMER_LOCATION";
            }
            String slug = blankToNull(signUpRequest.getSlug());
            providerListingService.updateProfile(
                    business,
                    displayName,
                    slug,
                    signUpRequest.getBio(),
                    serviceMode,
                    signUpRequest.getYearsExperience(),
                    signUpRequest.getPhoneNumber(),
                    signUpRequest.getPrimaryCategoryId());
            business = businessRepository.findById(business.getId()).orElse(business);
            business.setOnboardingState("PROFILE_COMPLETE");
            businessRepository.save(business);
            provisionedListing = business;
        }

        if (userRole == UserRole.BUSINESS_OWNER) {
            String businessName = signUpRequest.getBusinessName();
            if (businessName == null || businessName.isBlank()) {
                businessName = savedUser.getFirstName() + "'s Business";
            }

            Category primaryCategory = null;
            if (signUpRequest.getPrimaryCategoryId() != null) {
                primaryCategory = categoryRepository.findById(signUpRequest.getPrimaryCategoryId()).orElse(null);
            }
            if (primaryCategory == null && signUpRequest.getBusinessCategory() != null
                    && !signUpRequest.getBusinessCategory().isBlank()) {
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

            Organization organization = tenancyService.provisionOrganization(
                    savedUser,
                    businessName,
                    signUpRequest.getCurrency(),
                    signUpRequest.getCountryCode(),
                    signUpRequest.getRegion(),
                    signUpRequest.getCity(),
                    signUpRequest.getTimezone());
            Business business = Business.builder()
                    .name(businessName)
                    .organization(organization)
                    .description(signUpRequest.getBusinessDescription())
                    .registrationNumber(signUpRequest.getRegistrationNumber())
                    .primaryCategory(primaryCategory)
                    .phone(signUpRequest.getPhoneNumber())
                    .listingMode("BUSINESS")
                    .providerType("BUSINESS")
                    .status(BusinessStatus.PENDING)
                    .publishStatus("DRAFT")
                    .onboardingState("PROFILE_COMPLETE")
                    .build();
            business = businessRepository.save(business);

            if (signUpRequest.getAddress() != null && !signUpRequest.getAddress().isBlank()
                    && signUpRequest.getLatitude() != null && signUpRequest.getLongitude() != null) {
                branchRepository.save(Branch.builder()
                        .business(business)
                        .name(blankToNull(signUpRequest.getBranchName()) == null
                                ? "Main location" : signUpRequest.getBranchName().trim())
                        .address(signUpRequest.getAddress().trim())
                        .latitude(signUpRequest.getLatitude())
                        .longitude(signUpRequest.getLongitude())
                        .phoneNumber(signUpRequest.getPhoneNumber())
                        .countryCode(blankToNull(signUpRequest.getCountryCode()) == null
                                ? null : signUpRequest.getCountryCode().trim().toUpperCase())
                        .region(blankToNull(signUpRequest.getRegion()))
                        .city(blankToNull(signUpRequest.getCity()))
                        .timezone(blankToNull(signUpRequest.getTimezone()))
                        .build());
            }
            provisionedListing = business;
        }

        if (provisionedListing != null) {
            try {
                if (governmentId != null && !governmentId.isEmpty()) {
                    verificationDocumentService.upload(
                            provisionedListing,
                            BusinessVerificationDocument.OWNER_GOVERNMENT_ID,
                            governmentId);
                }
                if (tradeLicense != null && !tradeLicense.isEmpty()
                        && "BUSINESS".equals(accountType)) {
                    verificationDocumentService.upload(
                            provisionedListing,
                            BusinessVerificationDocument.TRADE_LICENSE,
                            tradeLicense);
                }
            } catch (Exception ex) {
                throw new IllegalArgumentException(
                        ex.getMessage() != null ? ex.getMessage() : "Could not save verification documents.");
            }
        }

        notificationPreferenceService.ensureDefaults(savedUser);
        issueEmailVerification(savedUser);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String resolveAccountType(RegisterRequest request) {
        if (request.getAccountType() != null && !request.getAccountType().isBlank()) {
            String type = request.getAccountType().trim().toUpperCase();
            if (!List.of("CUSTOMER", "INDIVIDUAL", "BUSINESS").contains(type)) {
                throw new IllegalArgumentException("accountType must be CUSTOMER, INDIVIDUAL, or BUSINESS.");
            }
            return type;
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String role = request.getRole().trim().toUpperCase();
            if ("BUSINESS_OWNER".equals(role) || "BUSINESS".equals(role)) {
                return "BUSINESS";
            }
            if ("INDIVIDUAL".equals(role)) {
                return "INDIVIDUAL";
            }
            if ("CUSTOMER".equals(role)) {
                return "CUSTOMER";
            }
            throw new IllegalArgumentException("Invalid role specified.");
        }
        return "CUSTOMER";
    }

    private LoginResponse buildLoginResponse(
            User user, List<String> roles, String appRole) {
        Optional<Business> business = tenancyService.findBusinessForUser(user);
        String listingMode = business.map(Business::getListingMode).orElse(null);
        String providerType = business.map(Business::getProviderType).orElse(null);
        String onboardingState = business.map(Business::getOnboardingState).orElse(null);
        Boolean online = business.map(Business::isOnline).orElse(null);
        return LoginResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(appRole)
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .authorities(roles)
                .listingMode(listingMode)
                .providerType(providerType)
                .providerIntent(user.getProviderIntent())
                .onboardingState(onboardingState)
                .providerWorkspace(business.isPresent())
                .isOnline(online)
                .build();
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
    public ResponseEntity<?> refreshToken(
            @RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        String requestRefreshToken = request != null && request.getRefreshToken() != null
                && !request.getRefreshToken().isBlank()
                ? request.getRefreshToken()
                : AuthCookies.read(httpRequest, AuthCookies.REFRESH);

        if (requestRefreshToken == null || !jwtUtils.validateJwtToken(requestRefreshToken) || !jwtUtils.isRefreshToken(requestRefreshToken)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Refresh token is expired or invalid!"));
        }

        Optional<AuthRefreshToken> stored = authRefreshTokenRepository
                .findByTokenHash(TokenHashes.sha256(requestRefreshToken));
        if (stored.isEmpty() || !stored.get().isActive()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Refresh token is expired or invalid!"));
        }

        String username = jwtUtils.getUsernameFromJwtToken(requestRefreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails,
                null, userDetails.getAuthorities());

        // Rotate: revoke presented token, issue a new refresh token.
        AuthRefreshToken current = stored.get();
        current.setRevokedAt(LocalDateTime.now());
        authRefreshTokenRepository.save(current);

        String token = jwtUtils.generateAccessToken(authentication);
        String newRefreshToken = jwtUtils.generateRefreshToken(authentication);
        if (userDetails instanceof CustomUserDetails custom) {
            userRepository.findById(custom.getId()).ifPresent(user -> persistRefreshToken(user, newRefreshToken));
        }

        authCookies.write(httpResponse, token, newRefreshToken);
        return ResponseEntity.ok(new MessageResponse("Session refreshed."));
    }

    /** Revokes the presented refresh token (if any) and clears the session cookies. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refresh = AuthCookies.read(httpRequest, AuthCookies.REFRESH);
        if (refresh != null) {
            authRefreshTokenRepository.findByTokenHash(TokenHashes.sha256(refresh)).ifPresent(t -> {
                t.setRevokedAt(LocalDateTime.now());
                authRefreshTokenRepository.save(t);
            });
        }
        authCookies.clear(httpResponse);
        return ResponseEntity.ok(new MessageResponse("Signed out."));
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
        authRefreshTokenRepository.revokeAllForUser(user.getId());
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
