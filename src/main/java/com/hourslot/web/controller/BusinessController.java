package com.hourslot.web.controller;

import com.hourslot.identity.dto.MessageResponse;
import com.hourslot.organization.model.Branch;
import com.hourslot.availability.model.BranchBreak;
import com.hourslot.availability.repository.BranchBreakRepository;
import com.hourslot.availability.model.BranchHoliday;
import com.hourslot.availability.repository.BranchHolidayRepository;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.availability.model.BranchWorkingHour;
import com.hourslot.availability.repository.BranchWorkingHourRepository;
import com.hourslot.availability.model.BranchWorkingInterval;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.model.BusinessStatus;
import com.hourslot.catalog.services.CatalogLocaleService;
import com.hourslot.catalog.model.Category;
import com.hourslot.catalog.repository.CategoryRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.identity.model.CustomerProfile;
import com.hourslot.identity.repository.CustomerProfileRepository;
import com.hourslot.organization.services.EntitlementService;
import com.hourslot.media.services.MediaAssetService;
import com.hourslot.organization.model.Organization;
import com.hourslot.organization.model.PlanEntitlement;
import com.hourslot.organization.repository.PlanEntitlementRepository;
import com.hourslot.organization.services.RbacService;
import com.hourslot.availability.services.ScheduleService;
import com.hourslot.catalog.model.Service;
import com.hourslot.catalog.model.ServicePackage;
import com.hourslot.catalog.repository.ServicePackageRepository;
import com.hourslot.catalog.repository.ServiceRepository;
import com.hourslot.organization.model.Staff;
import com.hourslot.availability.model.StaffBreak;
import com.hourslot.availability.repository.StaffBreakRepository;
import com.hourslot.organization.services.StaffInviteService;
import com.hourslot.organization.repository.StaffRepository;
import com.hourslot.catalog.model.StaffService;
import com.hourslot.catalog.repository.StaffServiceRepository;
import com.hourslot.availability.model.StaffTimeOff;
import com.hourslot.availability.repository.StaffTimeOffRepository;
import com.hourslot.availability.model.StaffWorkingHour;
import com.hourslot.availability.repository.StaffWorkingHourRepository;
import com.hourslot.availability.model.StaffWorkingInterval;
import com.hourslot.organization.repository.SubscriptionPlanRepository;
import com.hourslot.organization.services.TenancyService;
import com.hourslot.catalog.model.TimeOfDayPricing;
import com.hourslot.catalog.repository.TimeOfDayPricingRepository;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.model.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/business")
public class BusinessController {

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RbacService rbacService;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private MediaAssetService mediaAssetService;

    @Autowired
    private BranchWorkingHourRepository branchWorkingHourRepository;

    @Autowired
    private StaffWorkingHourRepository staffWorkingHourRepository;

    @Autowired
    private BranchBreakRepository branchBreakRepository;

    @Autowired
    private StaffBreakRepository staffBreakRepository;

    @Autowired
    private BranchHolidayRepository branchHolidayRepository;

    @Autowired
    private StaffTimeOffRepository staffTimeOffRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private TimeOfDayPricingRepository timeOfDayPricingRepository;

    @Autowired
    private EntitlementService entitlementService;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private PlanEntitlementRepository planEntitlementRepository;

    @Autowired
    private StaffInviteService staffInviteService;

    @Autowired
    private CatalogLocaleService catalogLocaleService;

    @Data
    public static class CustomerCreateRequest {
        @NotBlank
        private String firstName;
        @NotBlank
        private String lastName;
        @NotBlank
        @jakarta.validation.constraints.Email
        private String email;
        private String phoneNumber;
    }

    @Data
    public static class BusinessRegistrationRequest {
        @NotBlank
        private String name;
        private String description;
        private String logoUrl;
        private Long primaryCategoryId;
        private String registrationNumber;
        private String phoneNumber;
        private String address;
        private Double latitude;
        private Double longitude;
        private String branchName;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        private String defaultCurrency;
        private String timezone;
    }

    @Data
    public static class BranchRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String address;
        @NotNull
        private Double latitude;
        @NotNull
        private Double longitude;
        private String phoneNumber;
        private String countryCode;
        private String region;
        private String city;
        private String postalCode;
        private String timezone;
    }

    @Data
    public static class ServiceRequest {
        @NotBlank
        private String name;
        private String description;
        @NotNull
        private Double price;
        private String currency;
        @NotNull
        private Integer durationMinutes;
        private Integer bufferMinutes;
        private Integer maxConcurrent;
        private Boolean active;
        private Integer capacity;
        private Boolean groupService;
    }

    @Data
    public static class StaffRequest {
        @NotBlank
        private String name;
        private String designation;
        @NotNull
        private Long branchId;
        private Long userId; // optional linked user login account
        private String specialty;
        private String bio;
    }

    @Data
    public static class StaffInviteRequest {
        @NotBlank
        private String email;
        @NotBlank
        private String displayName;
        private String designation;
        @NotNull
        private Long branchId;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> registerBusiness(
            @Valid @RequestBody BusinessRegistrationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User owner = userRepository.findById(userDetails.getId()).orElseThrow();

        if (businessRepository.existsByMemberUserId(owner.getId())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: You have already registered a business!"));
        }

        Organization organization = tenancyService.provisionOrganization(
                owner,
                request.getName(),
                request.getDefaultCurrency(),
                request.getCountryCode(),
                request.getRegion(),
                request.getCity(),
                request.getTimezone());
        Category primaryCategory = null;
        if (request.getPrimaryCategoryId() != null) {
            primaryCategory = categoryRepository.findById(request.getPrimaryCategoryId()).orElse(null);
        }
        Business business = Business.builder()
                .name(request.getName())
                .description(request.getDescription())
                .organization(organization)
                .registrationNumber(request.getRegistrationNumber())
                .primaryCategory(primaryCategory)
                .status(BusinessStatus.PENDING)
                .build();
        business = businessRepository.save(business);
        if (request.getLogoUrl() != null && !request.getLogoUrl().isBlank()) {
            mediaAssetService.replaceLogo(business.getId(), request.getLogoUrl());
        }

        if (request.getAddress() != null && !request.getAddress().isBlank()
                && request.getLatitude() != null && request.getLongitude() != null) {
            Branch branch = Branch.builder()
                    .business(business)
                    .name(request.getBranchName() != null && !request.getBranchName().isBlank()
                            ? request.getBranchName()
                            : "Main location")
                    .address(request.getAddress())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .phoneNumber(request.getPhoneNumber())
                    .countryCode(blankToNullUpper(request.getCountryCode()))
                    .region(blankToNull(request.getRegion()))
                    .city(blankToNull(request.getCity()))
                    .postalCode(blankToNull(request.getPostalCode()))
                    .timezone(blankToNull(request.getTimezone()))
                    .build();
            branchRepository.save(branch);
        }

        return ResponseEntity.ok(new MessageResponse(
                "Business registration submitted. Upload Tier 1 documents so Super Admin can approve your listing."));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getBusinessProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(business);
    }

    @GetMapping("/plan")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<EntitlementService.OwnerPlanSnapshot> getPlan(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Organization organization = tenancyService.requireOrganizationForUser(user);
        return ResponseEntity.ok(entitlementService.snapshot(organization));
    }

    @GetMapping("/plans")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> listPlans(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Organization organization = tenancyService.requireOrganizationForUser(user);
        EntitlementService.OwnerPlanSnapshot current = entitlementService.snapshot(organization);
        List<Map<String, Object>> plans = subscriptionPlanRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(plan -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", plan.getCode());
                    row.put("name", plan.getName());
                    row.put("price", plan.getPrice());
                    row.put("currency", plan.getCurrency());
                    row.put("billingInterval", plan.getBillingInterval());
                    row.put("sortOrder", plan.getSortOrder());
                    row.put("features", plan.getFeatures());
                    Map<String, Object> entitlements = new LinkedHashMap<>();
                    for (PlanEntitlement pe : planEntitlementRepository.findByPlan(plan)) {
                        Object value = pe.getValue();
                        if ("BOOL".equalsIgnoreCase(pe.getValueType()) || "BOOLEAN".equalsIgnoreCase(pe.getValueType())) {
                            value = Boolean.parseBoolean(pe.getValue());
                        } else if ("INT".equalsIgnoreCase(pe.getValueType()) || "INTEGER".equalsIgnoreCase(pe.getValueType())) {
                            try {
                                value = Integer.parseInt(pe.getValue().trim());
                            } catch (NumberFormatException ignored) {
                                value = 0;
                            }
                        }
                        entitlements.put(pe.getEntitlementCode(), value);
                    }
                    row.put("entitlements", entitlements);
                    row.put("current", plan.getCode().equals(current.getPlanCode()));
                    return row;
                })
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("current", current);
        body.put("plans", plans);
        body.put("billingNote", "Stripe Billing checkout ships next. Your org stays on the current plan until then.");
        return ResponseEntity.ok(body);
    }

    // ==========================================================================
    // BRANCH CONFIGURATION ENDPOINTS
    // ==========================================================================

    @PostMapping("/branches")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addBranch(
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));
        Organization organization = organizationOf(business);
        entitlementService.requireHeadroom(
                organization,
                EntitlementService.MAX_BRANCHES,
                entitlementService.countBranches(organization),
                "branches");

        // Allow setup while PENDING so owners can complete onboarding before admin approval.
        // Bookings remain blocked until listing is APPROVED (Tier 1 docs). Verified badge is optional.

        if (request.getLatitude() == null || request.getLongitude() == null) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Latitude and longitude are required."));
        }

        Branch branch = Branch.builder()
                .business(business)
                .name(request.getName())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .phoneNumber(request.getPhoneNumber())
                .countryCode(blankToNullUpper(request.getCountryCode() != null
                        ? request.getCountryCode()
                        : organization.getCountryCode()))
                .region(blankToNull(request.getRegion() != null ? request.getRegion() : organization.getRegion()))
                .city(blankToNull(request.getCity() != null ? request.getCity() : organization.getCity()))
                .postalCode(blankToNull(request.getPostalCode()))
                .timezone(blankToNull(request.getTimezone() != null ? request.getTimezone() : organization.getTimezone()))
                .build();

        branchRepository.save(branch);

        return ResponseEntity.ok(new MessageResponse("Branch " + branch.getName() + " added successfully!"));
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<Branch>> getBranches(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        List<Branch> branches = branchRepository.findByBusiness(business);
        return ResponseEntity.ok(branches);
    }

    // ==========================================================================
    // SERVICE CONFIGURATION ENDPOINTS
    // ==========================================================================

    @PostMapping("/services")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addService(
            @Valid @RequestBody ServiceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        Service service = Service.builder()
                .business(business)
                .name(request.getName())
                .description(request.getDescription())
                .basePrice(java.math.BigDecimal.valueOf(request.getPrice()))
                .currency(catalogLocaleService.normalizeCurrency(request.getCurrency(), business))
                .durationMinutes(request.getDurationMinutes())
                .bufferMinutes(request.getBufferMinutes() != null ? request.getBufferMinutes() : 0)
                .maxConcurrent(request.getMaxConcurrent() != null ? request.getMaxConcurrent() : 1)
                .active(request.getActive() != null ? request.getActive() : true)
                .capacity(request.getCapacity() != null ? request.getCapacity() : 1)
                .groupService(Boolean.TRUE.equals(request.getGroupService()))
                .build();

        serviceRepository.save(service);

        return ResponseEntity.ok(new MessageResponse("Service " + service.getName() + " added successfully!"));
    }

    @GetMapping("/services")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<Service>> getServices(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        List<Service> services = serviceRepository.findByBusiness(business);
        return ResponseEntity.ok(services);
    }

    // ==========================================================================
    // STAFF CONFIGURATION ENDPOINTS
    // ==========================================================================

    @PostMapping("/staff")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addStaff(
            @Valid @RequestBody StaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        // Verify that the branch belongs to the authenticated admin's business
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        Organization organization = organizationOf(business);
        entitlementService.requireHeadroom(
                organization,
                EntitlementService.MAX_STAFF,
                entitlementService.countStaff(organization),
                "staff members");

        User staffUser = null;
        if (request.getUserId() != null) {
            staffUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Staff User login not found."));
        }

        Staff staff = Staff.builder()
                .branch(branch)
                .user(staffUser)
                .displayName(request.getName())
                .designation(request.getDesignation())
                .specialty(request.getSpecialty())
                .bio(request.getBio())
                .build();

        staffRepository.save(staff);

        return ResponseEntity.ok(new MessageResponse("Staff member " + staff.getName() + " added successfully!"));
    }

    @GetMapping("/staff/invites")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> listStaffInvites(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Organization organization = tenancyService.requireOrganizationForUser(user);
        return ResponseEntity.ok(staffInviteService.list(organization).stream()
                .map(staffInviteService::toView)
                .toList());
    }

    @PostMapping("/staff/invites")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> createStaffInvite(
            @Valid @RequestBody StaffInviteRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.requireBusinessForUser(user);
        Organization organization = organizationOf(business);
        return ResponseEntity.ok(staffInviteService.invite(
                organization,
                business,
                user,
                request.getBranchId(),
                request.getEmail(),
                request.getDisplayName(),
                request.getDesignation()));
    }

    @DeleteMapping("/staff/invites/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> revokeStaffInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Organization organization = tenancyService.requireOrganizationForUser(user);
        staffInviteService.revoke(organization, id);
        return ResponseEntity.ok(new MessageResponse("Invite revoked."));
    }

    @PutMapping("/staff/{id}/profile")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> updateStaffProfile(
            @PathVariable Long id,
            @RequestBody StaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found."));
        Business business = tenancyService.requireBusinessForUser(user);
        if (!tenancyService.staffBelongsToBusiness(staff, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Unauthorized."));
        }
        boolean isOwner = userDetails.getAuthorities().stream()
                .anyMatch(a -> "ROLE_BUSINESS_OWNER".equals(a.getAuthority()));
        boolean isSelf = staff.getUser() != null && staff.getUser().getId().equals(user.getId());
        if (!isOwner && !isSelf) {
            return ResponseEntity.status(403).body(new MessageResponse("Unauthorized."));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            staff.setDisplayName(request.getName());
        }
        if (request.getDesignation() != null) {
            staff.setDesignation(request.getDesignation());
        }
        if (request.getSpecialty() != null) {
            staff.setSpecialty(request.getSpecialty());
        }
        if (request.getBio() != null) {
            staff.setBio(request.getBio());
        }
        staffRepository.save(staff);
        return ResponseEntity.ok(staff);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getAllStaff(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business;
        if (userDetails.getRole() == UserRole.BUSINESS_OWNER) {
            business = tenancyService.findBusinessForUser(user)
                    .orElseThrow(() -> new RuntimeException("Business not found for owner."));
        } else {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (staff.getBranch() == null || staff.getBranch().getBusiness() == null) {
                throw new RuntimeException("Staff account is not linked to a business.");
            }
            business = staff.getBranch().getBusiness();
        }

        List<Staff> allStaff = staffRepository.findByBusiness(business);
        return ResponseEntity.ok(allStaff);
    }

    @GetMapping("/branches/{branchId}/staff")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getStaffByBranch(
            @PathVariable Long branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        if (userDetails.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = tenancyService.findBusinessForUser(user)
                    .orElseThrow(() -> new RuntimeException("Business not found for owner."));
            if (!tenancyService.branchBelongsToBusiness(branch, business)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
            }
        } else {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
            }
        }

        List<Staff> staffList = staffRepository.findByBranch(branch);
        return ResponseEntity.ok(staffList);
    }

    // ==========================================================================
    // WORKING HOURS, BREAKS & HOLIDAYS MANAGEMENT ENDPOINTS
    // ==========================================================================

    @Data
    public static class WorkingHourRequest {
        @NotNull
        private Long branchId;
        private Long staffId; // optional
        @NotNull
        private Integer dayOfWeek;
        private String startTime; // "HH:mm"
        private String endTime;   // "HH:mm"
        private Boolean closed;
        private Integer slotStepMinutes;
        private java.util.List<IntervalRequest> intervals;
    }

    @Data
    public static class BreakRequest {
        @NotBlank
        private String startTime; // "HH:mm"
        @NotBlank
        private String endTime;   // "HH:mm"
    }

    @Data
    public static class IntervalRequest {
        @NotBlank
        private String startTime; // "HH:mm"
        @NotBlank
        private String endTime;   // "HH:mm"
    }

    @Data
    public static class BatchWorkingHourRequest {
        @NotNull
        private Long branchId;
        private Long staffId; // optional
        private Integer slotStepMinutes;
        private java.util.List<DayConfigRequest> days;
    }

    @Data
    public static class DayConfigRequest {
        @NotNull
        private Integer dayOfWeek;
        private String startTime; // "HH:mm" (legacy envelope; optional if intervals provided)
        private String endTime;   // "HH:mm"
        @NotNull
        private Boolean closed;
        private java.util.List<IntervalRequest> intervals;
    }

    @Data
    public static class HolidayRequest {
        @NotNull
        private Long branchId;
        private Long staffId; // optional
        @NotBlank
        private String date; // "YYYY-MM-DD"
        private String description;
    }

    @PostMapping("/working-hours")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> configureWorkingHour(
            @Valid @RequestBody WorkingHourRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        // Validate owner
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        boolean closed = request.getClosed() != null && request.getClosed();
        int step = normalizeSlotStep(request.getSlotStepMinutes());
        java.util.List<java.time.LocalTime[]> windows = resolveIntervals(
                closed, request.getIntervals(), request.getStartTime(), request.getEndTime());
        java.time.LocalTime start = windows.isEmpty() ? null : windows.get(0)[0];
        java.time.LocalTime end = windows.isEmpty() ? null : windows.get(windows.size() - 1)[1];
        for (java.time.LocalTime[] w : windows) {
            if (start == null || w[0].isBefore(start)) start = w[0];
            if (end == null || w[1].isAfter(end)) end = w[1];
        }

        if (request.getStaffId() != null) {
            Staff staff = staffRepository.findById(request.getStaffId()).orElseThrow();
            StaffWorkingHour wh = StaffWorkingHour.builder()
                    .staff(staff)
                    .dayOfWeek(request.getDayOfWeek())
                    .startTime(start)
                    .endTime(end)
                    .closed(closed)
                    .slotStepMinutes(step)
                    .intervals(toStaffIntervals(windows))
                    .build();
            staffWorkingHourRepository.save(wh);
        } else {
            BranchWorkingHour wh = BranchWorkingHour.builder()
                    .branch(branch)
                    .dayOfWeek(request.getDayOfWeek())
                    .startTime(start)
                    .endTime(end)
                    .closed(closed)
                    .slotStepMinutes(step)
                    .intervals(toBranchIntervals(windows))
                    .build();
            branchWorkingHourRepository.save(wh);
        }

        return ResponseEntity.ok(new MessageResponse("Working hours updated successfully!"));
    }

    @PostMapping("/working-hours/batch")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> configureWorkingHoursBatch(
            @Valid @RequestBody BatchWorkingHourRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        // Validate owner
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        if (request.getDays() == null || request.getDays().isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Days configuration list cannot be empty."));
        }

        int step = normalizeSlotStep(request.getSlotStepMinutes());
        final Staff staff;
        if (request.getStaffId() != null) {
            staff = staffRepository.findById(request.getStaffId()).orElseThrow();
        } else {
            staff = null;
        }

        for (DayConfigRequest dayConfig : request.getDays()) {
            boolean closed = Boolean.TRUE.equals(dayConfig.getClosed());
            java.util.List<java.time.LocalTime[]> windows = resolveIntervals(
                    closed, dayConfig.getIntervals(), dayConfig.getStartTime(), dayConfig.getEndTime());
            java.time.LocalTime start = null;
            java.time.LocalTime end = null;
            for (java.time.LocalTime[] w : windows) {
                if (start == null || w[0].isBefore(start)) start = w[0];
                if (end == null || w[1].isAfter(end)) end = w[1];
            }

            if (staff != null) {
                java.util.Optional<StaffWorkingHour> existing =
                        staffWorkingHourRepository.findByStaffAndDayOfWeek(staff, dayConfig.getDayOfWeek());
                StaffWorkingHour wh = existing.orElseGet(() -> StaffWorkingHour.builder()
                        .staff(staff)
                        .dayOfWeek(dayConfig.getDayOfWeek())
                        .build());
                wh.setStartTime(start);
                wh.setEndTime(end);
                wh.setClosed(closed);
                wh.setSlotStepMinutes(step);
                wh.setIntervals(toStaffIntervals(windows));
                staffWorkingHourRepository.save(wh);
            } else {
                java.util.Optional<BranchWorkingHour> existing =
                        branchWorkingHourRepository.findByBranchAndDayOfWeek(branch, dayConfig.getDayOfWeek());
                BranchWorkingHour wh = existing.orElseGet(() -> BranchWorkingHour.builder()
                        .branch(branch)
                        .dayOfWeek(dayConfig.getDayOfWeek())
                        .build());
                wh.setStartTime(start);
                wh.setEndTime(end);
                wh.setClosed(closed);
                wh.setSlotStepMinutes(step);
                wh.setIntervals(toBranchIntervals(windows));
                branchWorkingHourRepository.save(wh);
            }
        }

        return ResponseEntity.ok(new MessageResponse("Working hours updated successfully!"));
    }

    private static int normalizeSlotStep(Integer step) {
        if (step == null) return 30;
        return switch (step) {
            case 10, 30, 60, 120 -> step;
            default -> 30;
        };
    }

    private static java.util.List<java.time.LocalTime[]> resolveIntervals(
            boolean closed,
            java.util.List<IntervalRequest> intervals,
            String legacyStart,
            String legacyEnd) {
        java.util.List<java.time.LocalTime[]> windows = new java.util.ArrayList<>();
        if (closed) {
            return windows;
        }
        if (intervals != null && !intervals.isEmpty()) {
            for (IntervalRequest interval : intervals) {
                if (interval == null || interval.getStartTime() == null || interval.getEndTime() == null) {
                    continue;
                }
                java.time.LocalTime s = java.time.LocalTime.parse(interval.getStartTime());
                java.time.LocalTime e = java.time.LocalTime.parse(interval.getEndTime());
                if (!e.isAfter(s)) {
                    throw new RuntimeException("Each open interval must end after it starts.");
                }
                windows.add(new java.time.LocalTime[]{s, e});
            }
        } else if (legacyStart != null && !legacyStart.isEmpty() && legacyEnd != null && !legacyEnd.isEmpty()) {
            java.time.LocalTime s = java.time.LocalTime.parse(legacyStart);
            java.time.LocalTime e = java.time.LocalTime.parse(legacyEnd);
            if (!e.isAfter(s)) {
                throw new RuntimeException("End time must be after start time.");
            }
            windows.add(new java.time.LocalTime[]{s, e});
        }
        windows.sort(java.util.Comparator.comparing(w -> w[0]));
        return windows;
    }

    private static java.util.List<BranchWorkingInterval> toBranchIntervals(java.util.List<java.time.LocalTime[]> windows) {
        java.util.List<BranchWorkingInterval> list = new java.util.ArrayList<>();
        int order = 0;
        for (java.time.LocalTime[] w : windows) {
            list.add(BranchWorkingInterval.builder()
                    .startTime(w[0])
                    .endTime(w[1])
                    .sortOrder(order++)
                    .build());
        }
        return list;
    }

    private static java.util.List<StaffWorkingInterval> toStaffIntervals(java.util.List<java.time.LocalTime[]> windows) {
        java.util.List<StaffWorkingInterval> list = new java.util.ArrayList<>();
        int order = 0;
        for (java.time.LocalTime[] w : windows) {
            list.add(StaffWorkingInterval.builder()
                    .startTime(w[0])
                    .endTime(w[1])
                    .sortOrder(order++)
                    .build());
        }
        return list;
    }

    @PostMapping("/working-hours/{workingHourId}/breaks")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addBreakToWorkingHour(
            @PathVariable Long workingHourId,
            @Valid @RequestBody BreakRequest request) {
        
        java.util.Optional<BranchWorkingHour> branchHour = branchWorkingHourRepository.findById(workingHourId);
        if (branchHour.isPresent()) {
            BranchBreak restBreak = BranchBreak.builder()
                    .workingHour(branchHour.get())
                    .startTime(java.time.LocalTime.parse(request.getStartTime()))
                    .endTime(java.time.LocalTime.parse(request.getEndTime()))
                    .build();
            branchBreakRepository.save(restBreak);
            return ResponseEntity.ok(new MessageResponse("Break period added successfully!"));
        }
        StaffWorkingHour staffHour = staffWorkingHourRepository.findById(workingHourId)
                .orElseThrow(() -> new RuntimeException("Working hours record not found."));
        StaffBreak restBreak = StaffBreak.builder()
                .workingHour(staffHour)
                .startTime(java.time.LocalTime.parse(request.getStartTime()))
                .endTime(java.time.LocalTime.parse(request.getEndTime()))
                .build();
        staffBreakRepository.save(restBreak);
        return ResponseEntity.ok(new MessageResponse("Break period added successfully!"));
    }

    @PostMapping("/holidays")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addHoliday(
            @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        // Validate owner
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        if (request.getStaffId() != null) {
            Staff staff = staffRepository.findById(request.getStaffId()).orElseThrow();
            java.time.LocalDate date = java.time.LocalDate.parse(request.getDate());
            staffTimeOffRepository.save(StaffTimeOff.builder()
                    .staff(staff)
                    .startAt(date.atStartOfDay())
                    .endAt(date.atTime(java.time.LocalTime.MAX))
                    .reason(request.getDescription())
                    .status("APPROVED")
                    .build());
        } else {
            branchHolidayRepository.save(BranchHoliday.builder()
                    .branch(branch)
                    .holidayDate(java.time.LocalDate.parse(request.getDate()))
                    .description(request.getDescription())
                    .build());
        }

        return ResponseEntity.ok(new MessageResponse("Holiday date registered successfully!"));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateBusinessProfile(
            @Valid @RequestBody BusinessUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User owner = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(owner)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        business.setName(request.getName());
        business.setDescription(request.getDescription());
        business.setRegistrationNumber(request.getRegistrationNumber());

        if (request.getPrimaryCategoryId() != null) {
            Category primary = categoryRepository.findById(request.getPrimaryCategoryId())
                    .orElseThrow(() -> new RuntimeException("Primary category not found."));
            business.setPrimaryCategory(primary);
        } else {
            business.setPrimaryCategory(null);
        }

        if (request.getSecondaryCategoryIds() != null && !request.getSecondaryCategoryIds().isEmpty()) {
            List<Category> secondaries = categoryRepository.findAllById(request.getSecondaryCategoryIds());
            business.setSecondaryCategories(secondaries);
        } else {
            business.setSecondaryCategories(new java.util.ArrayList<>());
        }

        business.setSlug(business.getName().toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .trim());

        businessRepository.save(business);
        if (request.getLogoUrl() != null) {
            mediaAssetService.replaceLogo(business.getId(), request.getLogoUrl());
        }
        if (request.getGalleryUrls() != null) {
            mediaAssetService.replaceGallery(business.getId(), request.getGalleryUrls());
        }
        return ResponseEntity.ok(new MessageResponse("Business profile updated successfully!"));
    }

    @GetMapping("/profile-by-slug/{slug}")
    public ResponseEntity<?> getBusinessProfileBySlug(@PathVariable String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Business not found for slug: " + slug));
        return ResponseEntity.ok(business);
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateBranch(
            @PathVariable Long id,
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        if (request.getLatitude() == null || request.getLongitude() == null) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Latitude and longitude are required."));
        }

        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setLatitude(request.getLatitude());
        branch.setLongitude(request.getLongitude());
        branch.setPhoneNumber(request.getPhoneNumber());
        if (request.getCountryCode() != null) {
            branch.setCountryCode(blankToNullUpper(request.getCountryCode()));
        }
        if (request.getRegion() != null) {
            branch.setRegion(blankToNull(request.getRegion()));
        }
        if (request.getCity() != null) {
            branch.setCity(blankToNull(request.getCity()));
        }
        if (request.getPostalCode() != null) {
            branch.setPostalCode(blankToNull(request.getPostalCode()));
        }
        if (request.getTimezone() != null) {
            branch.setTimezone(blankToNull(request.getTimezone()));
        }

        branchRepository.save(branch);
        return ResponseEntity.ok(new MessageResponse("Branch updated successfully!"));
    }

    @DeleteMapping("/branches/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteBranch(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        branchRepository.delete(branch);
        return ResponseEntity.ok(new MessageResponse("Branch deleted successfully!"));
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized service access."));
        }

        service.setName(request.getName());
        service.setDescription(request.getDescription());
        service.setPrice(request.getPrice());
        service.setCurrency(catalogLocaleService.normalizeCurrency(request.getCurrency(), business));
        service.setDurationMinutes(request.getDurationMinutes());
        if (request.getBufferMinutes() != null) {
            service.setBufferMinutes(request.getBufferMinutes());
        }
        if (request.getMaxConcurrent() != null) {
            service.setMaxConcurrent(request.getMaxConcurrent());
        }
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }
        if (request.getCapacity() != null) {
            service.setCapacity(request.getCapacity());
        }
        if (request.getGroupService() != null) {
            service.setGroupService(request.getGroupService());
        }

        serviceRepository.save(service);
        return ResponseEntity.ok(new MessageResponse("Service updated successfully!"));
    }

    @DeleteMapping("/services/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteService(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized service access."));
        }

        serviceRepository.delete(service);
        return ResponseEntity.ok(new MessageResponse("Service deleted successfully!"));
    }

    @PutMapping("/staff/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff member not found."));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.staffBelongsToBusiness(staff, business) ||
            !tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        User staffUser = null;
        if (request.getUserId() != null) {
            staffUser = userRepository.findById(request.getUserId()).orElse(null);
        }

        staff.setName(request.getName());
        staff.setDesignation(request.getDesignation());
        staff.setBranch(branch);
        staff.setUser(staffUser);

        staffRepository.save(staff);
        return ResponseEntity.ok(new MessageResponse("Staff member updated successfully!"));
    }

    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteStaff(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff member not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.staffBelongsToBusiness(staff, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        staffRepository.delete(staff);
        return ResponseEntity.ok(new MessageResponse("Staff member deleted successfully!"));
    }

    @GetMapping("/branches/{branchId}/working-hours")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getWorkingHours(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long staffId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Staff does not belong to this branch."));
            }
            return ResponseEntity.ok(staffWorkingHourRepository.findByStaffOrderByDayOfWeekAsc(staff));
        }
        return ResponseEntity.ok(branchWorkingHourRepository.findByBranchOrderByDayOfWeekAsc(branch));
    }

    @GetMapping("/branches/{branchId}/holidays")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getHolidays(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long staffId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!tenancyService.branchBelongsToBusiness(branch, business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Staff does not belong to this branch."));
            }
            return ResponseEntity.ok(staffTimeOffRepository.findByStaffOrderByStartAtAsc(staff));
        }
        return ResponseEntity.ok(branchHolidayRepository.findByBranchOrderByHolidayDateAsc(branch));
    }

    @DeleteMapping("/working-hours/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteWorkingHour(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        java.util.Optional<BranchWorkingHour> branchHour = branchWorkingHourRepository.findById(id);
        if (branchHour.isPresent()) {
            if (!tenancyService.branchBelongsToBusiness(branchHour.get().getBranch(), business)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchWorkingHourRepository.delete(branchHour.get());
            return ResponseEntity.ok(new MessageResponse("Working hour record removed successfully!"));
        }
        StaffWorkingHour staffHour = staffWorkingHourRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Working hours record not found."));
        if (!tenancyService.staffBelongsToBusiness(staffHour.getStaff(), business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        staffWorkingHourRepository.delete(staffHour);
        return ResponseEntity.ok(new MessageResponse("Working hour record removed successfully!"));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteHoliday(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        java.util.Optional<BranchHoliday> holiday = branchHolidayRepository.findById(id);
        if (holiday.isPresent()) {
            if (!tenancyService.branchBelongsToBusiness(holiday.get().getBranch(), business)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchHolidayRepository.delete(holiday.get());
            return ResponseEntity.ok(new MessageResponse("Holiday date cancelled successfully!"));
        }
        StaffTimeOff timeOff = staffTimeOffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday record not found."));
        if (!tenancyService.staffBelongsToBusiness(timeOff.getStaff(), business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        staffTimeOffRepository.delete(timeOff);
        return ResponseEntity.ok(new MessageResponse("Holiday date cancelled successfully!"));
    }

    @DeleteMapping("/breaks/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteBreak(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        java.util.Optional<BranchBreak> branchBreak = branchBreakRepository.findById(id);
        if (branchBreak.isPresent()) {
            BranchWorkingHour hour = branchBreak.get().getWorkingHour();
            if (hour != null && hour.getId() != null && hour.getBranch() == null) {
                hour = branchWorkingHourRepository.findById(hour.getId()).orElse(hour);
            }
            if (!tenancyService.branchBelongsToBusiness(hour == null ? null : hour.getBranch(), business)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchBreakRepository.delete(branchBreak.get());
            return ResponseEntity.ok(new MessageResponse("Break period removed successfully!"));
        }
        StaffBreak staffBreak = staffBreakRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Break record not found."));
        StaffWorkingHour staffHour = staffBreak.getWorkingHour();
        if (staffHour != null && staffHour.getId() != null
                && (staffHour.getStaff() == null || staffHour.getStaff().getBranch() == null)) {
            staffHour = staffWorkingHourRepository.findById(staffHour.getId()).orElse(staffHour);
        }
        if (!tenancyService.staffBelongsToBusiness(staffHour == null ? null : staffHour.getStaff(), business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        staffBreakRepository.delete(staffBreak);
        return ResponseEntity.ok(new MessageResponse("Break period removed successfully!"));
    }

    @Data
    public static class BusinessUpdateRequest {
        @NotBlank
        private String name;
        private String description;
        private String logoUrl;
        private String registrationNumber;
        private String galleryUrls;
        private Long primaryCategoryId;
        private List<Long> secondaryCategoryIds;
    }

    // ==========================================================================
    // SERVICE PACKAGE MANAGEMENT ENDPOINTS
    // ==========================================================================

    @Data
    public static class PackageRequest {
        @NotBlank
        private String name;
        private String description;
        @NotNull
        private Double price;
        private String currency;
        @NotNull
        private Integer sessionsCount;
        private Integer expiryDays;
        private Boolean active;
        private List<Long> serviceIds;
    }

    @GetMapping("/packages")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<ServicePackage>> getPackages(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(servicePackageRepository.findByBusiness(business));
    }

    @PostMapping("/packages")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> createPackage(
            @Valid @RequestBody PackageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        entitlementService.requireFeature(organizationOf(business), EntitlementService.PACKAGES, "session packages");

        List<Service> services = serviceRepository.findAllById(request.getServiceIds());

        ServicePackage pkg = ServicePackage.builder()
                .business(business)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(catalogLocaleService.normalizeCurrency(request.getCurrency(), business))
                .sessionsCount(request.getSessionsCount())
                .expiryDays(request.getExpiryDays() != null ? request.getExpiryDays() : 0)
                .active(request.getActive() != null ? request.getActive() : true)
                .services(services)
                .build();

        servicePackageRepository.save(pkg);
        return ResponseEntity.ok(new MessageResponse("Service package created successfully!"));
    }

    @PutMapping("/packages/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody PackageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ServicePackage pkg = servicePackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!pkg.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        entitlementService.requireFeature(organizationOf(business), EntitlementService.PACKAGES, "session packages");

        List<Service> services = serviceRepository.findAllById(request.getServiceIds());

        pkg.setName(request.getName());
        pkg.setDescription(request.getDescription());
        pkg.setPrice(request.getPrice());
        pkg.setCurrency(catalogLocaleService.normalizeCurrency(request.getCurrency(), business));
        pkg.setSessionsCount(request.getSessionsCount());
        pkg.setExpiryDays(request.getExpiryDays() != null ? request.getExpiryDays() : 0);
        if (request.getActive() != null) {
            pkg.setActive(request.getActive());
        }
        pkg.setServices(services);

        servicePackageRepository.save(pkg);
        return ResponseEntity.ok(new MessageResponse("Service package updated successfully!"));
    }

    @DeleteMapping("/packages/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deletePackage(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ServicePackage pkg = servicePackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Package not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!pkg.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        servicePackageRepository.delete(pkg);
        return ResponseEntity.ok(new MessageResponse("Service package removed successfully."));
    }

    // ==========================================================================
    // STAFF SERVICE ASSIGNMENT ENDPOINTS
    // ==========================================================================

    @Data
    public static class StaffServiceReq {
        @NotNull
        private Long staffId;
        @NotNull
        private Long serviceId;
        private Double priceOverride;
    }

    @GetMapping("/staff-services")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<StaffService>> getStaffServices(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(staffServiceRepository.findByStaffBranchBusiness(business));
    }

    @PostMapping("/staff-services")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> assignStaffService(
            @Valid @RequestBody StaffServiceReq request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new RuntimeException("Staff member not found."));
        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!tenancyService.staffBelongsToBusiness(staff, business) ||
            !service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        if (staffServiceRepository.findByStaffAndService(staff, service).isPresent()) {
            return ResponseEntity.status(409)
                    .body(new MessageResponse("This specialist already offers that service."));
        }

        StaffService ss = StaffService.builder()
                .staff(staff)
                .service(service)
                .priceOverride(request.getPriceOverride())
                .build();

        staffServiceRepository.save(ss);
        StaffService saved = staffServiceRepository.findById(ss.getId()).orElse(ss);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/staff-services/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateStaffService(
            @PathVariable Long id,
            @Valid @RequestBody StaffServiceReq request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        StaffService ss = staffServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment record not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!tenancyService.staffBelongsToBusiness(ss.getStaff(), business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        ss.setPriceOverride(request.getPriceOverride());
        staffServiceRepository.save(ss);
        return ResponseEntity.ok(new MessageResponse("Assignment rate updated successfully!"));
    }

    @DeleteMapping("/staff-services/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> unassignStaffService(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        StaffService ss = staffServiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Assignment record not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!tenancyService.staffBelongsToBusiness(ss.getStaff(), business)) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        staffServiceRepository.delete(ss);
        return ResponseEntity.ok(new MessageResponse("Staff service assignment removed."));
    }

    // ==========================================================================
    // TIME OF DAY (PEAK) PRICING ENDPOINTS
    // ==========================================================================

    @Data
    public static class TimePricingRequest {
        @NotNull
        private Long serviceId;
        @NotNull
        private Integer dayOfWeek;
        @NotBlank
        private String startTime; // "HH:mm"
        @NotBlank
        private String endTime;   // "HH:mm"
        @NotNull
        private Double priceMultiplier;
    }

    @GetMapping("/time-pricing")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<TimeOfDayPricing>> getTimePricingRules(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(timeOfDayPricingRepository.findByServiceBusiness(business));
    }

    @PostMapping("/time-pricing")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> createTimePricing(
            @Valid @RequestBody TimePricingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new RuntimeException("Service not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        entitlementService.requireFeature(organizationOf(business), EntitlementService.PEAK_PRICING, "peak pricing");

        TimeOfDayPricing top = TimeOfDayPricing.builder()
                .service(service)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(java.time.LocalTime.parse(request.getStartTime()))
                .endTime(java.time.LocalTime.parse(request.getEndTime()))
                .priceMultiplier(request.getPriceMultiplier())
                .build();

        timeOfDayPricingRepository.save(top);
        return ResponseEntity.ok(new MessageResponse("Peak pricing override saved!"));
    }

    @PutMapping("/time-pricing/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateTimePricing(
            @PathVariable Long id,
            @Valid @RequestBody TimePricingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TimeOfDayPricing top = timeOfDayPricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pricing override rule not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!top.getService().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }
        entitlementService.requireFeature(organizationOf(business), EntitlementService.PEAK_PRICING, "peak pricing");

        top.setDayOfWeek(request.getDayOfWeek());
        top.setStartTime(java.time.LocalTime.parse(request.getStartTime()));
        top.setEndTime(java.time.LocalTime.parse(request.getEndTime()));
        top.setPriceMultiplier(request.getPriceMultiplier());

        timeOfDayPricingRepository.save(top);
        return ResponseEntity.ok(new MessageResponse("Peak pricing override updated!"));
    }

    @DeleteMapping("/time-pricing/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteTimePricing(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TimeOfDayPricing top = timeOfDayPricingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pricing override rule not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = tenancyService.findBusinessForUser(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!top.getService().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        timeOfDayPricingRepository.delete(top);
        return ResponseEntity.ok(new MessageResponse("Peak pricing rule deleted."));
    }

    // ==========================================================================
    // CUSTOMER MANAGEMENT ENDPOINTS
    // ==========================================================================

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getCustomers() {
        List<User> users = userRepository.findAll();
        rbacService.attachAppRoles(users);
        List<User> customers = users.stream()
                .filter(u -> u.getRole() == UserRole.CUSTOMER)
                .toList();
        return ResponseEntity.ok(customers);
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    @Transactional
    public ResponseEntity<?> createCustomer(@Valid @RequestBody CustomerCreateRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        customerProfileRepository.save(CustomerProfile.builder()
                .user(savedUser)
                .build());

        rbacService.grantSystemRole(savedUser, "CUSTOMER", null, null, null, null);

        // attach app role so UI receives it
        savedUser.setRole(UserRole.CUSTOMER);

        return ResponseEntity.ok(savedUser);
    }

    private Organization organizationOf(Business business) {
        Organization organization = business.getOrganization();
        if (organization == null) {
            throw new RuntimeException("Organization not found.");
        }
        return organization;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String blankToNullUpper(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }
}
