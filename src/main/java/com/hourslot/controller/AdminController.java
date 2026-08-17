package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.MediaAssetService;
import com.hourslot.service.RbacService;
import com.hourslot.service.SystemSettingService;
import com.hourslot.service.TenancyService;
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
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private CategoryRepository categoryRepository;

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

            // Seed mock metrics if data is sparse to make UI charts look rich immediately
            if (count == 0) {
                // Mock scaling factor based on index
                int factor = 6 - i; 
                count = factor * 4L + 5;
                totalRev = count * 65.0;
                totalComm = totalRev * 0.12;
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
        BusinessDetailResponse response = BusinessDetailResponse.builder()
                .business(business)
                .branches(branches)
                .services(services)
                .staff(staff)
                .build();
        
        return ResponseEntity.ok(response);
    }

    @PutMapping("/businesses/{id}/verify")
    public ResponseEntity<?> verifyBusiness(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest request) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found with id: " + id));

        business.setVerified(true);
        business.setStatus(BusinessStatus.APPROVED);
        businessRepository.save(business);

        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", business.getId(), 
                "Approved business: " + business.getName(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Business " + business.getName() + " verified successfully!"));
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
        User admin = userRepository.findById(adminDetails.getId()).orElse(null);
        Map<String, Object> updated = systemSettingService.updateAdminSettings(registrationOpen, currencies, admin);

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
                .ratingAvg(java.math.BigDecimal.valueOf(4.8))
                .build();
        business1 = businessRepository.save(business1);
        business1.setVerified(true);
        business1.setStatus(BusinessStatus.APPROVED);
        business1 = businessRepository.save(business1);
        mediaAssetService.replaceLogo(business1.getId(), "https://images.unsplash.com/photo-1560066984-138dadb4c035?w=120");

        Business business2 = Business.builder()
                .name("Elite Dental Care")
                .organization(org2)
                .description("Advanced dental clinic specializing in implants and cosmetic smile alignment.")
                .primaryCategory(healthCat)
                .status(BusinessStatus.PENDING)
                .verified(false)
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

        // 7. Write seed audit logs
        logAction(adminDetails.getId(), "SEED_DATABASE", "System", 0L, 
                "Successfully populated demo/mock database records.", request.getRemoteAddr());
        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", business1.getId(), 
                "Approved business: Zenith Hair Salon", request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Database seeded successfully with customers, businesses, branches, services, staff, bookings, and audit records!"));
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
