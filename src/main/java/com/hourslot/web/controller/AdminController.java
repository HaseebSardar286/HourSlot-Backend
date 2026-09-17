package com.hourslot.web.controller;

import com.hourslot.identity.dto.MessageResponse;
import com.hourslot.organization.model.AuditEvent;
import com.hourslot.organization.repository.AuditEventRepository;
import com.hourslot.booking.model.Booking;
import com.hourslot.booking.model.BookingItem;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.booking.model.BookingStatus;
import com.hourslot.organization.model.Branch;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.model.BusinessStatus;
import com.hourslot.organization.model.BusinessVerificationDocument;
import com.hourslot.organization.repository.BusinessVerificationDocumentRepository;
import com.hourslot.catalog.model.Category;
import com.hourslot.catalog.repository.CategoryRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.identity.model.CustomerProfile;
import com.hourslot.identity.repository.CustomerProfileRepository;
import com.hourslot.media.services.MediaAssetService;
import com.hourslot.organization.model.Organization;
import com.hourslot.organization.services.RbacService;
import com.hourslot.catalog.model.Service;
import com.hourslot.catalog.repository.ServiceRepository;
import com.hourslot.organization.model.Staff;
import com.hourslot.organization.repository.StaffRepository;
import com.hourslot.organization.model.SystemSetting;
import com.hourslot.organization.services.SystemSettingService;
import com.hourslot.organization.services.TenancyService;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.organization.services.VerificationDocumentService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private SystemSettingService systemSettingService;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private VerificationDocumentService verificationDocumentService;

    @Autowired
    private BusinessVerificationDocumentRepository businessVerificationDocumentRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private com.hourslot.geo.repository.GeoAreaRepository geoAreaRepository;

    @Autowired
    private com.hourslot.organization.services.ServiceAreaService serviceAreaService;

    @PostConstruct
    public void initDefaultConfig() {
        // Settings are seeded by Flyway (system_settings).
    }

    // ==========================================
    // 1. DASHBOARD & STATS ENDPOINTS
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlatformStats {
        private long totalUsers;
        private long totalBusinesses;
        private long totalBookings;
        private double totalCommissionEarnings;
        private long pendingVerifications;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<PlatformStats> getPlatformStats() {
        long totalUsers = userRepository.count();
        long totalBusinesses = businessRepository.count();
        long totalBookings = bookingRepository.count();
        long pendingVerifications = businessRepository.findByVerified(false).size();

        // Platform never takes a booking cut — businesses keep 100% of visit revenue.
        double totalCommission = 0.0;

        PlatformStats stats = PlatformStats.builder()
                .totalUsers(totalUsers)
                .totalBusinesses(totalBusinesses)
                .totalBookings(totalBookings)
                .totalCommissionEarnings(Math.round(totalCommission * 100.0) / 100.0)
                .pendingVerifications(pendingVerifications)
                .build();

        return ResponseEntity.ok(stats);
    }

    @Data
    @Builder
    public static class RecentRegistrationsResponse {
        private List<User> users;
        private List<Business> businesses;
    }

    @GetMapping("/dashboard/recent-registrations")
    public ResponseEntity<RecentRegistrationsResponse> getRecentRegistrations() {
        List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());

        List<Business> businesses = businessRepository.findAll().stream()
                .sorted(Comparator.comparing(Business::getCreatedAt).reversed())
                .limit(10)
                .collect(Collectors.toList());
        rbacService.attachAppRoles(users);
        tenancyService.attachOwners(businesses);

        RecentRegistrationsResponse response = RecentRegistrationsResponse.builder()
                .users(users)
                .businesses(businesses)
                .build();

        return ResponseEntity.ok(response);
    }

    @Data
    @AllArgsConstructor
    public static class RevenueTrendPoint {
        private String month;
        private long bookings;
        private double totalRevenue;
        private double commissionEarnings;
    }

    @GetMapping("/dashboard/revenue-trend")
    public ResponseEntity<List<RevenueTrendPoint>> getRevenueTrend() {
        // Group completed bookings from past 6 months
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Booking> bookings = bookingRepository.findAll().stream()
                .filter(b -> b.getBookingTime().isAfter(sixMonthsAgo))
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED || b.getStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.toList());

        Map<String, List<Booking>> groupedByMonth = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getBookingTime().format(DateTimeFormatter.ofPattern("MMM yyyy"))));

        List<RevenueTrendPoint> trend = new ArrayList<>();
        
        // Populate last 6 months in chronological order
        for (int i = 5; i >= 0; i--) {
            LocalDateTime targetMonth = LocalDateTime.now().minusMonths(i);
            String monthKey = targetMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"));
            List<Booking> monthBookings = groupedByMonth.getOrDefault(monthKey, new ArrayList<>());

            long count = monthBookings.size();
            double totalRev = monthBookings.stream().mapToDouble(Booking::getPrice).sum();
            double totalComm = 0.0;

            if (count == 0) {
                trend.add(new RevenueTrendPoint(
                        targetMonth.format(DateTimeFormatter.ofPattern("MMM")),
                        0,
                        0.0,
                        0.0
                ));
                continue;
            }

            trend.add(new RevenueTrendPoint(
                    targetMonth.format(DateTimeFormatter.ofPattern("MMM")),
                    count,
                    Math.round(totalRev * 100.0) / 100.0,
                    Math.round(totalComm * 100.0) / 100.0
            ));
        }

        return ResponseEntity.ok(trend);
    }

    // ==========================================
    // 2. USER MANAGEMENT ENDPOINTS
    // ==========================================

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search) {
        
        List<User> users = userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .collect(Collectors.toList());
        rbacService.attachAppRoles(users);

        if (role != null && !role.isBlank()) {
            users = users.stream()
                    .filter(u -> u.getRole().name().equalsIgnoreCase(role))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String query = search.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getEmail().toLowerCase().contains(query) ||
                            (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(query)) ||
                            (u.getLastName() != null && u.getLastName().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        rbacService.attachAppRoles(List.of(user));
        return ResponseEntity.ok(user);
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<User> toggleUserStatus(
            @PathVariable Long id, 
            @RequestParam boolean active,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setActive(active);
        userRepository.save(user);

        logAction(adminDetails.getId(), "TOGGLE_USER_STATUS", "User", user.getId(), 
                "Set active status of " + user.getEmail() + " to " + active, request.getRemoteAddr());

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        // Delete customer profile if exists
        customerProfileRepository.findById(id).ifPresent(customerProfileRepository::delete);
        
        userRepository.delete(user);

        logAction(adminDetails.getId(), "DELETE_USER", "User", id, 
                "Deleted user account: " + user.getEmail(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("User deleted successfully."));
    }

    // ==========================================
    // 3. BUSINESS MANAGEMENT ENDPOINTS
    // ==========================================

    @GetMapping("/businesses")
    public ResponseEntity<List<Business>> getAllBusinesses(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        
        List<Business> businesses = businessRepository.findAll().stream()
                .sorted(Comparator.comparing(Business::getCreatedAt).reversed())
                .collect(Collectors.toList());

        if (status != null && !status.isBlank()) {
            businesses = businesses.stream()
                    .filter(b -> b.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String query = search.toLowerCase();
            businesses = businesses.stream()
                    .filter(b -> b.getName().toLowerCase().contains(query) ||
                            (b.getDescription() != null && b.getDescription().toLowerCase().contains(query)))
                    .collect(Collectors.toList());
        }

        tenancyService.attachOwners(businesses);
        return ResponseEntity.ok(businesses);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessDetailResponse {
        private Business business;
        private List<Branch> branches;
        private List<Service> services;
        private List<Staff> staff;
        private List<Map<String, Object>> verificationDocuments;
        private Map<String, Object> verificationReadiness;
    }

    @GetMapping("/businesses/{id}")
    public ResponseEntity<BusinessDetailResponse> getBusinessById(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));
        
        List<Branch> branches = branchRepository.findByBusiness(business);
        List<Service> services = serviceRepository.findByBusiness(business);
        
        List<Staff> staff = new ArrayList<>();
        if (branches != null) {
            for (Branch b : branches) {
                List<Staff> branchStaff = staffRepository.findByBranch(b);
                if (branchStaff != null) {
                    staff.addAll(branchStaff);
                }
            }
        }

        tenancyService.attachOwner(business);
        List<Map<String, Object>> docs = verificationDocumentService.list(business).stream()
                .map(doc -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("id", doc.getId());
                    view.put("documentType", doc.getDocumentType());
                    view.put("label", VerificationDocumentService.labelFor(doc.getDocumentType()));
                    view.put("hint", VerificationDocumentService.hintFor(doc.getDocumentType()));
                    view.put("tier", VerificationDocumentService.tierFor(doc.getDocumentType()));
                    view.put("status", doc.getStatus());
                    view.put("originalFilename", doc.getOriginalFilename());
                    view.put("url", doc.getMediaAsset() != null ? doc.getMediaAsset().getUrl() : null);
                    view.put("reviewNotes", doc.getReviewNotes());
                    view.put("reviewedAt", doc.getReviewedAt());
                    view.put("createdAt", doc.getCreatedAt());
                    return view;
                })
                .toList();
        BusinessDetailResponse response = BusinessDetailResponse.builder()
                .business(business)
                .branches(branches)
                .services(services)
                .staff(staff)
                .verificationDocuments(docs)
                .verificationReadiness(verificationDocumentService.readiness(business))
                .build();
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/businesses/{id}/approve")
    public ResponseEntity<?> approveBusinessListing(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));
        verificationDocumentService.requireReadyForListing(business);
        business.setStatus(BusinessStatus.APPROVED);
        business.setRejectionReason(null);
        businessRepository.save(business);
        logAction(adminDetails.getId(), "APPROVE_BUSINESS", "Business", business.getId(),
                "Approved listing: " + business.getName(), request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse("Business " + business.getName() + " listing approved."));
    }

    @PutMapping("/businesses/{id}/verify")
    public ResponseEntity<?> verifyBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));

        verificationDocumentService.requireReadyForVerifiedBadge(business);

        business.setVerified(true);
        business.setStatus(BusinessStatus.APPROVED);
        business.setRejectionReason(null);
        businessRepository.save(business);

        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", business.getId(), 
                "Verified badge granted: " + business.getName(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Verified badge granted for " + business.getName() + "."));
    }

    @Data
    public static class DocumentReviewRequest {
        private boolean approve;
        private String notes;
    }

    @PutMapping("/businesses/{businessId}/verification-documents/{documentId}/review")
    public ResponseEntity<?> reviewVerificationDocument(
            @PathVariable Long businessId,
            @PathVariable Long documentId,
            @RequestBody DocumentReviewRequest body,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        BusinessVerificationDocument document = businessVerificationDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found."));
        if (!document.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Document does not belong to this business."));
        }
        User admin = userRepository.findById(adminDetails.getId()).orElseThrow();
        boolean approve = body != null && body.isApprove();
        String notes = body != null ? body.getNotes() : null;
        verificationDocumentService.review(document, admin, approve, notes);
        logAction(adminDetails.getId(), approve ? "APPROVE_KYC_DOC" : "REJECT_KYC_DOC",
                "BusinessVerificationDocument", document.getId(),
                document.getDocumentType() + " for business " + business.getId(),
                request.getRemoteAddr());
        return ResponseEntity.ok(Map.of(
                "message", approve ? "Document approved." : "Document rejected.",
                "readiness", verificationDocumentService.readiness(business)
        ));
    }

    @Data
    public static class RejectionRequest {
        private String reason;
    }

    @PutMapping("/businesses/{id}/reject")
    public ResponseEntity<?> rejectBusiness(
            @PathVariable Long id,
            @RequestBody RejectionRequest rejection,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));

        business.setVerified(false);
        business.setStatus(BusinessStatus.REJECTED);
        business.setRejectionReason(rejection.getReason());
        businessRepository.save(business);

        logAction(adminDetails.getId(), "REJECT_BUSINESS", "Business", business.getId(), 
                "Rejected business: " + business.getName() + ". Reason: " + rejection.getReason(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Business " + business.getName() + " rejected."));
    }

    @PutMapping("/businesses/{id}/suspend")
    public ResponseEntity<?> suspendBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));

        business.setVerified(false);
        business.setStatus(BusinessStatus.SUSPENDED);
        businessRepository.save(business);

        logAction(adminDetails.getId(), "SUSPEND_BUSINESS", "Business", business.getId(), 
                "Suspended business: " + business.getName(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Business " + business.getName() + " suspended successfully."));
    }

    @PutMapping("/businesses/{id}/commission")
    public ResponseEntity<?> updateCommission(
            @PathVariable Long id,
            @RequestParam double rate,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));

        logAction(adminDetails.getId(), "UPDATE_COMMISSION", "Business", business.getId(),
                "Commission is unused (requested " + rate + "%). HourSlot does not take a booking cut.",
                request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(
                "Commission is unused. HourSlot does not take a cut of bookings."));
    }

    // ==========================================
    // 4. SYSTEM CONFIG ENDPOINTS
    // ==========================================

    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings() {
        return ResponseEntity.ok(systemSettingService.asAdminSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody Map<String, Object> settingsUpdate,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        boolean registrationOpen = Boolean.parseBoolean(String.valueOf(settingsUpdate.getOrDefault("registrationOpen", true)));
        String currencies = String.valueOf(settingsUpdate.getOrDefault("supportedCurrencies", "USD,PKR,AED,EUR,GBP"));
        String defaultCurrency = String.valueOf(settingsUpdate.getOrDefault("defaultCurrency", "USD"));
        User admin = userRepository.findById(adminDetails.getId()).orElse(null);
        Map<String, Object> updated = systemSettingService.updateAdminSettings(
                registrationOpen, currencies, defaultCurrency, admin);

        logAction(adminDetails.getId(), "UPDATE_SYSTEM_SETTINGS", "SystemSetting", 0L,
                "Updated platform settings", request.getRemoteAddr());

        return ResponseEntity.ok(updated);
    }

    // ==========================================
    // 5. AUDIT LOGS ENDPOINT
    // ==========================================

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditEvent>> getAuditLogs() {
        return ResponseEntity.ok(auditEventRepository.findAllByOrderByCreatedAtDesc());
    }

    // ==========================================
    // 6. MOCK DATA SEEDER ENDPOINT (IDEMPOTENT)
    // ==========================================

    @PostMapping("/seed")
    public ResponseEntity<?> seedMockData(
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {

        String activeProfiles = System.getProperty("spring.profiles.active",
                System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "default"));
        if (activeProfiles.contains("prod")) {
            return ResponseEntity.status(403)
                    .body(new MessageResponse("Seeding is disabled in production."));
        }

        // Avoid duplicate seeding
        if (userRepository.findByEmail("alice@hourslot.com").isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Database already seeded with mock data."));
        }

        // 1. Seed Mock Users
        // Owner 1
        User ownerUser = User.builder()
                .email("zenithowner@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Robert")
                .lastName("Kowalski")
                .phoneNumber("+15551212")
                .status("ACTIVE")
                .build();
        userRepository.save(ownerUser);

        // Owner 2
        User ownerUser2 = User.builder()
                .email("elitedental@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Dr. Fatima")
                .lastName("Zahra")
                .phoneNumber("+15551313")
                .status("ACTIVE")
                .build();
        userRepository.save(ownerUser2);

        // Staff 1
        User staffUser = User.builder()
                .email("johndoe@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("John")
                .lastName("Doe")
                .phoneNumber("+15551414")
                .status("ACTIVE")
                .build();
        userRepository.save(staffUser);

        // Customer 1
        User custUser = User.builder()
                .email("alice@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Alice")
                .lastName("Green")
                .phoneNumber("+15550101")
                .status("ACTIVE")
                .build();
        userRepository.save(custUser);
        
        customerProfileRepository.save(CustomerProfile.builder()
                .user(custUser)
                .addressLine1("101 Emerald St")
                .gender("Female")
                .dateOfBirth(LocalDate.of(1995, 8, 15))
                .build());
        rbacService.grantSystemRole(custUser, "CUSTOMER", null, null, null, null);

        // Customer 2
        User custUser2 = User.builder()
                .email("bob@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Bob")
                .lastName("Miller")
                .phoneNumber("+15550102")
                .status("ACTIVE")
                .build();
        userRepository.save(custUser2);

        customerProfileRepository.save(CustomerProfile.builder()
                .user(custUser2)
                .addressLine1("202 Sapphire Rd")
                .gender("Male")
                .dateOfBirth(LocalDate.of(1990, 4, 20))
                .build());
        rbacService.grantSystemRole(custUser2, "CUSTOMER", null, null, null, null);

        // Seed Categories
        Category salonCat = Category.builder()
                .name("Salons")
                .icon("fa-scissors")
                .active(true)
                .build();
        categoryRepository.save(salonCat);

        Category healthCat = Category.builder()
                .name("Healthcare")
                .icon("fa-user-doctor")
                .active(true)
                .build();
        categoryRepository.save(healthCat);

        // 2. Seed Mock Businesses
        // Business 1 (Approved)
        Organization org1 = tenancyService.provisionOrganization(ownerUser, "Zenith Hair Salon");
        Organization org2 = tenancyService.provisionOrganization(ownerUser2, "Elite Dental Care");

        Business business1 = Business.builder()
                .name("Zenith Hair Salon")
                .organization(org1)
                .description("Luxury haircut, colors, and styling services in downtown.")
                .primaryCategory(salonCat)
                .status(BusinessStatus.APPROVED)
                .verified(true)
                .listingMode("BUSINESS")
                .providerType("BUSINESS")
                .serviceMode("AT_PROVIDER")
                .publishStatus("PUBLISHED")
                .ratingAvg(java.math.BigDecimal.valueOf(4.8))
                .build();
        business1 = businessRepository.save(business1);
        business1.setVerified(true);
        business1.setStatus(BusinessStatus.APPROVED);
        business1.setPublishStatus("PUBLISHED");
        business1 = businessRepository.save(business1);
        mediaAssetService.replaceLogo(business1.getId(), "https://images.unsplash.com/photo-1560066984-138dadb4c035?w=120");

        Business business2 = Business.builder()
                .name("Elite Dental Care")
                .organization(org2)
                .description("Advanced dental clinic specializing in implants and cosmetic smile alignment.")
                .primaryCategory(healthCat)
                .status(BusinessStatus.PENDING)
                .verified(false)
                .listingMode("BUSINESS")
                .providerType("BUSINESS")
                .serviceMode("AT_PROVIDER")
                .publishStatus("DRAFT")
                .build();
        business2 = businessRepository.save(business2);
        mediaAssetService.replaceLogo(business2.getId(), "https://images.unsplash.com/photo-1629909613654-28e377c37b09?w=120");

        // 3. Seed Branches
        Branch branch1 = Branch.builder()
                .business(business1)
                .name("Zenith Salon Downtown")
                .address("123 Main St, San Francisco, CA")
                .phoneNumber("+15553030")
                .latitude(37.7749)
                .longitude(-122.4194)
                .build();
        branchRepository.save(branch1);

        Branch branch2 = Branch.builder()
                .business(business2)
                .name("Elite Dental Bay Area")
                .address("456 Medical Blvd, San Francisco, CA")
                .phoneNumber("+15554040")
                .latitude(37.7849)
                .longitude(-122.4094)
                .build();
        branchRepository.save(branch2);

        // 4. Seed Services
        Service service1 = Service.builder()
                .name("Classic Haircut & Styling")
                .description("Includes hairwash, styling, and premium massage.")
                .basePrice(java.math.BigDecimal.valueOf(45.0))
                .business(business1)
                .durationMinutes(45)
                .build();
        serviceRepository.save(service1);

        Service service2 = Service.builder()
                .name("Full Hair Coloring")
                .description("Professional highlights and base color treatment.")
                .basePrice(java.math.BigDecimal.valueOf(120.0))
                .business(business1)
                .durationMinutes(90)
                .build();
        serviceRepository.save(service2);

        Service service3 = Service.builder()
                .name("Teeth Whitening")
                .description("Laser cleaning and whitening treatment.")
                .basePrice(java.math.BigDecimal.valueOf(180.0))
                .business(business2)
                .durationMinutes(60)
                .build();
        serviceRepository.save(service3);

        // 5. Seed Staff
        Staff staff1 = Staff.builder()
                .branch(branch1)
                .user(staffUser)
                .displayName("John Doe")
                .designation("Master Barber")
                .ratingAvg(java.math.BigDecimal.valueOf(4.9))
                .build();
        staffRepository.save(staff1);
        rbacService.grantSystemRole(staffUser, "STAFF", org1, business1, branch1, staff1);

        Staff staff2 = Staff.builder()
                .branch(branch1)
                .displayName("Sarah Jenkins")
                .designation("Senior Colorist")
                .ratingAvg(java.math.BigDecimal.valueOf(4.7))
                .build();
        staffRepository.save(staff2);

        seedBooking(custUser, business1, branch1, service1, staff1, LocalDateTime.now().minusMonths(3).withHour(10).withMinute(0), 45, BookingStatus.COMPLETED, 45.0);
        seedBooking(custUser2, business1, branch1, service2, staff2, LocalDateTime.now().minusMonths(2).withHour(14).withMinute(0), 90, BookingStatus.COMPLETED, 120.0);
        seedBooking(custUser, business1, branch1, service1, staff1, LocalDateTime.now().plusDays(2).withHour(11).withMinute(0), 45, BookingStatus.CONFIRMED, 45.0);

        Category homeCat = Category.builder()
                .name("Home services")
                .icon("fa-house")
                .active(true)
                .build();
        categoryRepository.save(homeCat);

        User electricianOwner = User.builder()
                .email("demo.electrician@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Demo")
                .lastName("Electrician")
                .phoneNumber("+923001111111")
                .status("ACTIVE")
                .build();
        userRepository.save(electricianOwner);
        Business electrician = tenancyService.provisionIndividualProvider(
                electricianOwner, "Demo Electrician Lahore", "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        electrician.setPrimaryCategory(homeCat);
        electrician.setDescription("Demo individual electrician covering named Lahore areas. Quote work is not slot-bookable.");
        electrician.setServiceMode("CUSTOMER_LOCATION");
        electrician.setStatus(BusinessStatus.APPROVED);
        electrician.setPublishStatus("PUBLISHED");
        electrician.setOnboardingState("PUBLISHED");
        electrician.setYearsExperience(8);
        electrician.setSlug("demo-electrician-lahore");
        businessRepository.save(electrician);

        Service wiringQuote = Service.builder()
                .business(electrician)
                .name("Demo wiring estimate")
                .description("On-site assessment before quote. Requests arrive in Phase 2.")
                .basePrice(java.math.BigDecimal.ZERO)
                .currency("PKR")
                .durationMinutes(60)
                .pricingType("QUOTE")
                .requiresQuote(true)
                .serviceMode("CUSTOMER_LOCATION")
                .allowsHomeService(true)
                .build();
        serviceRepository.save(wiringQuote);

        Service outletFix = Service.builder()
                .business(electrician)
                .name("Demo outlet repair")
                .description("Fixed-price visit for a single outlet repair.")
                .basePrice(java.math.BigDecimal.valueOf(2500))
                .currency("PKR")
                .durationMinutes(45)
                .pricingType("FIXED")
                .serviceMode("CUSTOMER_LOCATION")
                .allowsHomeService(true)
                .build();
        serviceRepository.save(outletFix);

        geoAreaRepository.findActiveByCity("PK", "Lahore").stream().limit(2).forEach(area ->
                serviceAreaService.create(electrician, com.hourslot.organization.model.BusinessServiceArea.builder()
                        .coverageType("NAMED")
                        .geoArea(area)
                        .areaName(area.getName())
                        .status("ACTIVE")
                        .build()));

        User cleanerOwner = User.builder()
                .email("demo.cleaner@hourslot.com")
                .passwordHash(encoder.encode("password123"))
                .firstName("Demo")
                .lastName("Cleaner")
                .phoneNumber("+923002222222")
                .status("ACTIVE")
                .build();
        userRepository.save(cleanerOwner);
        Business cleaner = tenancyService.provisionIndividualProvider(
                cleanerOwner, "Demo Cleaner Gulberg", "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        cleaner.setPrimaryCategory(homeCat);
        cleaner.setDescription("Demo hybrid cleaner with radius coverage and a bookable fixed home visit.");
        cleaner.setServiceMode("HYBRID");
        cleaner.setStatus(BusinessStatus.APPROVED);
        cleaner.setPublishStatus("PUBLISHED");
        cleaner.setOnboardingState("PUBLISHED");
        cleaner.setYearsExperience(5);
        cleaner.setTravelBufferMinutes(20);
        cleaner.setSlug("demo-cleaner-gulberg");
        businessRepository.save(cleaner);

        Service deepClean = Service.builder()
                .business(cleaner)
                .name("Demo deep clean visit")
                .description("Bookable fixed-price home clean on the implicit branch.")
                .basePrice(java.math.BigDecimal.valueOf(4500))
                .currency("PKR")
                .durationMinutes(120)
                .bufferMinutes(20)
                .pricingType("FIXED")
                .serviceMode("CUSTOMER_LOCATION")
                .allowsHomeService(true)
                .build();
        serviceRepository.save(deepClean);

        serviceAreaService.create(cleaner, com.hourslot.organization.model.BusinessServiceArea.builder()
                .coverageType("RADIUS")
                .areaName("Demo Gulberg radius")
                .latitude(31.5102)
                .longitude(74.3441)
                .radiusKm(java.math.BigDecimal.valueOf(8))
                .status("ACTIVE")
                .build());

        seedLahoreHomeDemo("demo.plumber@hourslot.com", "Plumber", "Demo Plumber Model Town",
                "demo-plumber-model-town", homeCat, "Demo on-site plumbing diagnosis and repair.",
                "Demo leak repair", "Fixed-price visit to stop a leaking tap or pipe joint.", 3200, 60);
        seedLahoreHomeDemo("demo.ac@hourslot.com", "Technician", "Demo AC Repair Johar Town",
                "demo-ac-johar-town", homeCat, "Demo AC service covering named Lahore areas.",
                "Demo AC gas top-up quote", "Quote visit before refrigerant work.", 0, 50);
        seedLahoreHomeDemo("demo.carpenter@hourslot.com", "Carpenter", "Demo Carpenter DHA",
                "demo-carpenter-dha", homeCat, "Demo carpenter for furniture repair at the customer location.",
                "Demo door hinge repair", "Fixed-price hinge and latch repair visit.", 2800, 75);

        // 7. Write seed audit logs
        logAction(adminDetails.getId(), "SEED_DATABASE", "System", 0L, 
                "Successfully populated demo/mock database records.", request.getRemoteAddr());
        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", business1.getId(), 
                "Approved business: Zenith Hair Salon", request.getRemoteAddr());
        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", electrician.getId(),
                "Approved demo individual: Demo Electrician Lahore", request.getRemoteAddr());
        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", cleaner.getId(),
                "Approved demo individual: Demo Cleaner Gulberg", request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Database seeded successfully with customers, businesses, branches, services, staff, bookings, and audit records!"));
    }

    private void seedLahoreHomeDemo(
            String email, String lastName, String displayName, String slug, Category category,
            String bio, String serviceName, String serviceDescription, double price, int durationMinutes) {
        User owner = User.builder()
                .email(email)
                .passwordHash(encoder.encode("password123"))
                .firstName("Demo")
                .lastName(lastName)
                .phoneNumber("+92300" + Math.abs(email.hashCode() % 1000000))
                .status("ACTIVE")
                .build();
        userRepository.save(owner);
        Business provider = tenancyService.provisionIndividualProvider(
                owner, displayName, "PK", "Punjab", "Lahore", "Asia/Karachi", "PKR");
        provider.setPrimaryCategory(category);
        provider.setDescription(bio);
        provider.setServiceMode("CUSTOMER_LOCATION");
        provider.setStatus(BusinessStatus.APPROVED);
        provider.setPublishStatus("PUBLISHED");
        provider.setOnboardingState("PUBLISHED");
        provider.setYearsExperience(6);
        provider.setSlug(slug);
        businessRepository.save(provider);

        boolean quoteOnly = price <= 0;
        serviceRepository.save(Service.builder()
                .business(provider)
                .name(serviceName)
                .description(serviceDescription)
                .basePrice(java.math.BigDecimal.valueOf(price))
                .currency("PKR")
                .durationMinutes(durationMinutes)
                .pricingType(quoteOnly ? "QUOTE" : "FIXED")
                .requiresQuote(quoteOnly)
                .serviceMode("CUSTOMER_LOCATION")
                .allowsHomeService(true)
                .build());

        geoAreaRepository.findActiveByCity("PK", "Lahore").stream().limit(2).forEach(area ->
                serviceAreaService.create(provider, com.hourslot.organization.model.BusinessServiceArea.builder()
                        .coverageType("NAMED")
                        .geoArea(area)
                        .areaName(area.getName())
                        .status("ACTIVE")
                        .build()));
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private void logAction(Long adminId, String action, String entity, Long entityId, String details, String ipAddress) {
        User admin = userRepository.findById(adminId).orElse(null);
        AuditEvent log = AuditEvent.builder()
                .actor(admin)
                .action(action + " - " + details)
                .entityType(entity)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .build();
        auditEventRepository.save(log);
    }

    private void seedBooking(User customer, Business business, Branch branch, Service service, Staff staff,
                             LocalDateTime start, int durationMinutes, BookingStatus status, double price) {
        Booking booking = Booking.builder()
                .customerUser(customer)
                .organization(business.getOrganization())
                .business(business)
                .branch(branch)
                .bookingTime(start)
                .endTime(start.plusMinutes(durationMinutes))
                .status(status)
                .totalPrice(java.math.BigDecimal.valueOf(price))
                .currency("USD")
                .paymentStatus("PAID")
                .paymentMethod("VENUE")
                .source("MARKETPLACE")
                .items(new java.util.ArrayList<>())
                .build();
        BookingItem item = BookingItem.builder()
                .booking(booking)
                .service(service)
                .staff(staff)
                .startTime(start)
                .endTime(start.plusMinutes(durationMinutes))
                .unitPrice(java.math.BigDecimal.valueOf(price))
                .priceMultiplier(java.math.BigDecimal.ONE)
                .lineTotal(java.math.BigDecimal.valueOf(price))
                .sortOrder(0)
                .build();
        booking.getItems().add(item);
        Booking saved = bookingRepository.save(booking);
        saved.setPublicCode("HS" + String.format("%08d", saved.getId()));
        bookingRepository.save(saved);
    }
}
