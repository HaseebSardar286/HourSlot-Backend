package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.EntitlementService;
import com.hourslot.service.MediaAssetService;
import com.hourslot.service.ScheduleService;
import com.hourslot.service.StaffInviteService;
import com.hourslot.service.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    }

    @Data
    public static class ServiceRequest {
        @NotBlank
        private String name;
        private String description;
        @NotNull
        private Double price;
        @NotNull
        private Integer durationMinutes;
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

        Organization organization = tenancyService.provisionOrganization(owner, request.getName());
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
                    .build();
            branchRepository.save(branch);
        }

        return ResponseEntity.ok(new MessageResponse(
                "Business registration submitted. Complete verification documents so Super Admin can grant a verified badge."));
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
        // Bookings remain blocked until APPROVED + verified.

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
                .durationMinutes(request.getDurationMinutes())
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

        if (!branch.getBusiness().getId().equals(business.getId())) {
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
        if (!staff.getBranch().getBusiness().getId().equals(business.getId())) {
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
            business = staff.getBranch().getBusiness();
        }

        List<Staff> allStaff = branchRepository.findByBusiness(business).stream()
                .flatMap(b -> staffRepository.findByBranch(b).stream())
                .toList();
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
            if (!branch.getBusiness().getId().equals(business.getId())) {
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
    }

    @Data
    public static class BreakRequest {
        @NotBlank
        private String startTime; // "HH:mm"
        @NotBlank
        private String endTime;   // "HH:mm"
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
        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        java.time.LocalTime start = request.getStartTime() != null ? java.time.LocalTime.parse(request.getStartTime()) : null;
        java.time.LocalTime end = request.getEndTime() != null ? java.time.LocalTime.parse(request.getEndTime()) : null;

        if (request.getStaffId() != null) {
            Staff staff = staffRepository.findById(request.getStaffId()).orElseThrow();
            StaffWorkingHour wh = StaffWorkingHour.builder()
                    .staff(staff)
                    .dayOfWeek(request.getDayOfWeek())
                    .startTime(start)
                    .endTime(end)
                    .closed(request.getClosed() != null ? request.getClosed() : false)
                    .build();
            staffWorkingHourRepository.save(wh);
        } else {
            BranchWorkingHour wh = BranchWorkingHour.builder()
                    .branch(branch)
                    .dayOfWeek(request.getDayOfWeek())
                    .startTime(start)
                    .endTime(end)
                    .closed(request.getClosed() != null ? request.getClosed() : false)
                    .build();
            branchWorkingHourRepository.save(wh);
        }

        return ResponseEntity.ok(new MessageResponse("Working hours updated successfully!"));
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
        if (!branch.getBusiness().getId().equals(business.getId())) {
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

        if (!branch.getBusiness().getId().equals(business.getId())) {
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

        if (!branch.getBusiness().getId().equals(business.getId())) {
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
        service.setDurationMinutes(request.getDurationMinutes());

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

        if (!staff.getBranch().getBusiness().getId().equals(business.getId()) ||
            !branch.getBusiness().getId().equals(business.getId())) {
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

        if (!staff.getBranch().getBusiness().getId().equals(business.getId())) {
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

        if (!branch.getBusiness().getId().equals(business.getId())) {
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

        if (!branch.getBusiness().getId().equals(business.getId())) {
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
            if (!branchHour.get().getBranch().getBusiness().getId().equals(business.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchWorkingHourRepository.delete(branchHour.get());
            return ResponseEntity.ok(new MessageResponse("Working hour record removed successfully!"));
        }
        StaffWorkingHour staffHour = staffWorkingHourRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Working hours record not found."));
        if (!staffHour.getStaff().getBranch().getBusiness().getId().equals(business.getId())) {
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
            if (!holiday.get().getBranch().getBusiness().getId().equals(business.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchHolidayRepository.delete(holiday.get());
            return ResponseEntity.ok(new MessageResponse("Holiday date cancelled successfully!"));
        }
        StaffTimeOff timeOff = staffTimeOffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday record not found."));
        if (!timeOff.getStaff().getBranch().getBusiness().getId().equals(business.getId())) {
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
            if (!branchBreak.get().getWorkingHour().getBranch().getBusiness().getId().equals(business.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
            }
            branchBreakRepository.delete(branchBreak.get());
            return ResponseEntity.ok(new MessageResponse("Break period removed successfully!"));
        }
        StaffBreak staffBreak = staffBreakRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Break record not found."));
        if (!staffBreak.getWorkingHour().getStaff().getBranch().getBusiness().getId().equals(business.getId())) {
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

        if (!staff.getBranch().getBusiness().getId().equals(business.getId()) ||
            !service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        StaffService ss = StaffService.builder()
                .staff(staff)
                .service(service)
                .priceOverride(request.getPriceOverride())
                .build();

        staffServiceRepository.save(ss);
        return ResponseEntity.ok(new MessageResponse("Service assigned to staff successfully!"));
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

        if (!ss.getStaff().getBranch().getBusiness().getId().equals(business.getId())) {
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

        if (!ss.getStaff().getBranch().getBusiness().getId().equals(business.getId())) {
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

    private Organization organizationOf(Business business) {
        Organization organization = business.getOrganization();
        if (organization == null) {
            throw new RuntimeException("Organization not found.");
        }
        return organization;
    }
}
