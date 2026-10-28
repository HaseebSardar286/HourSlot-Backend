package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.organization.model.AuditEvent;
import com.duemet.organization.repository.AuditEventRepository;
import com.duemet.booking.model.Booking;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.model.BookingStatus;
import com.duemet.organization.model.Branch;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.organization.model.BusinessVerificationDocument;
import com.duemet.organization.repository.BusinessVerificationDocumentRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.organization.services.RbacService;
import com.duemet.catalog.model.Service;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.organization.services.SystemSettingService;
import com.duemet.organization.services.TenancyService;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.organization.services.VerificationDocumentService;
import com.duemet.catalog.repository.CategoryRepository;
import com.duemet.media.services.ObjectStorageService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private static final Logger log = LogManager.getLogger(AdminController.class);

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
    private ObjectStorageService objectStorageService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostConstruct
    public void initDefaultConfig() {
        // Settings are seeded by Flyway (system_settings).
    }

    @PostMapping("/category-images")
    public ResponseEntity<?> uploadCategoryImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) Long categoryId) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("File is empty."));
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(new MessageResponse("Only image uploads are allowed."));
        }

        String ext = contentType.contains("png") ? ".png"
                : contentType.contains("webp") ? ".webp"
                : contentType.contains("gif") ? ".gif" : ".jpg";
        byte[] bytes = file.getBytes();
        String folder = objectStorageService.categoriesFolder();
        String publicUrl = null;

        if (objectStorageService.isConfigured()) {
            try {
                ObjectStorageService.StoredObject stored = objectStorageService.upload(
                        folder,
                        "cat",
                        contentType,
                        bytes,
                        ext);
                publicUrl = stored.publicUrl();
            } catch (Exception ex) {
                log.warn("R2 category upload failed, falling back to local disk: {}", ex.getMessage());
            }
        }

        if (publicUrl == null) {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(folder);
            Files.createDirectories(dir);
            String filename = "cat-" + UUID.randomUUID() + ext;
            Files.write(dir.resolve(filename), bytes);
            publicUrl = "/uploads/" + folder + "/" + filename;
        }

        final String savedUrl = publicUrl;
        if (categoryId != null) {
            categoryRepository.findById(categoryId).ifPresent(category -> {
                category.setImageUrl(savedUrl);
                categoryRepository.save(category);
            });
        }

        return ResponseEntity.ok(Map.of("url", savedUrl));
    }

    // ==========================================
    // 1. DASHBOARD & STATS ENDPOINTS
    // ==========================================

    public record PlatformStats(
            long totalUsers,
            long totalBusinesses,
            long totalBookings,
            double totalCommissionEarnings,
            long pendingVerifications) {
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<PlatformStats> getPlatformStats() {
        long totalUsers = userRepository.count();
        long totalBusinesses = businessRepository.count();
        long totalBookings = bookingRepository.count();
        long pendingVerifications = businessRepository.findByVerified(false).size();

        // Platform never takes a booking cut - businesses keep 100% of visit revenue.
        double totalCommission = 0.0;

        return ResponseEntity.ok(new PlatformStats(
                totalUsers,
                totalBusinesses,
                totalBookings,
                Math.round(totalCommission * 100.0) / 100.0,
                pendingVerifications));
    }

    public record RecentRegistrationsResponse(List<User> users, List<Business> businesses) {
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

        return ResponseEntity.ok(new RecentRegistrationsResponse(users, businesses));
    }

    public record RevenueTrendPoint(
            String month,
            long bookings,
            double totalRevenue,
            double commissionEarnings) {
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

    public record BusinessDetailResponse(
            Business business,
            List<Branch> branches,
            List<Service> services,
            List<Staff> staff,
            List<Map<String, Object>> verificationDocuments,
            Map<String, Object> verificationReadiness) {
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
                    view.put("url", doc.getMediaAsset() != null ? VerificationDocumentService.adminFileUrl(doc) : null);
                    view.put("reviewNotes", doc.getReviewNotes());
                    view.put("reviewedAt", doc.getReviewedAt());
                    view.put("createdAt", doc.getCreatedAt());
                    return view;
                })
                .toList();
        return ResponseEntity.ok(new BusinessDetailResponse(
                business,
                branches,
                services,
                staff,
                docs,
                verificationDocumentService.readiness(business)));
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

        if (business.getStatus() != BusinessStatus.APPROVED) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Approve the listing before granting the verified badge."));
        }
        verificationDocumentService.requireReadyForVerifiedBadge(business);

        business.setVerified(true);
        business.setRejectionReason(null);
        businessRepository.save(business);

        logAction(adminDetails.getId(), "VERIFY_BUSINESS", "Business", business.getId(), 
                "Verified badge granted: " + business.getName(), request.getRemoteAddr());

        return ResponseEntity.ok(new MessageResponse("Verified badge granted for " + business.getName() + "."));
    }

    public record DocumentReviewRequest(boolean approve, String notes) {
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
        boolean approve = body != null && body.approve();
        String notes = body != null ? body.notes() : null;
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

    @GetMapping("/businesses/{businessId}/verification-documents/{documentId}/file")
    public ResponseEntity<byte[]> verificationDocumentFile(
            @PathVariable Long businessId,
            @PathVariable Long documentId) throws Exception {
        BusinessVerificationDocument document = businessVerificationDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found."));
        if (!document.getBusiness().getId().equals(businessId)) {
            throw new RuntimeException("Document not found.");
        }
        return VerificationDocumentController.fileResponse(verificationDocumentService.loadFile(document));
    }

    public record RejectionRequest(String reason) {
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
        business.setPublishStatus("UNPUBLISHED");
        business.setRejectionReason(rejection != null ? rejection.reason() : null);
        businessRepository.save(business);

        logAction(adminDetails.getId(), "REJECT_BUSINESS", "Business", business.getId(),
                "Rejected business: " + business.getName() + ". Reason: "
                        + (rejection != null ? rejection.reason() : null),
                request.getRemoteAddr());

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
        business.setPublishStatus("UNPUBLISHED");
        business.setOnline(false);
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
                "Commission is unused (requested " + rate + "%). Duemet does not take a booking cut.",
                request.getRemoteAddr());
        return ResponseEntity.ok(new MessageResponse(
                "Commission is unused. Duemet does not take a cut of bookings."));
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

    private void logAction(Long adminId, String action, String entity, Long entityId, String details, String ipAddress) {
        User admin = userRepository.findById(adminId).orElse(null);
        AuditEvent log = new AuditEvent();
        log.setActor(admin);
        log.setAction(action + " - " + details);
        log.setEntityType(entity);
        log.setEntityId(entityId);
        log.setIpAddress(ipAddress);
        auditEventRepository.save(log);
    }
}
