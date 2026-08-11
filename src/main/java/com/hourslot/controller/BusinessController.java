package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.security.CustomUserDetails;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

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
    private TimeOfDayPricingRepository timeOfDayPricingRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Data
    public static class BusinessRegistrationRequest {
        @NotBlank
        private String name;
        private String description;
        private String logoUrl;
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
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> registerBusiness(
            @Valid @RequestBody BusinessRegistrationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User owner = userRepository.findById(userDetails.getId()).orElseThrow();

        if (businessRepository.existsByOwner(owner)) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: You have already registered a business!"));
        }

        Business business = Business.builder()
                .name(request.getName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .owner(owner)
                .build();

        businessRepository.save(business);

        return ResponseEntity.ok(new MessageResponse("Business registration request submitted successfully! Pending admin verification."));
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getBusinessProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(business);
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        // Allow setup while PENDING so owners can complete onboarding before admin approval.
        // Bookings remain blocked until APPROVED + verified.

        // Generate JTS coordinates Point geometry
        Point geom = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        Branch branch = Branch.builder()
                .business(business)
                .name(request.getName())
                .address(request.getAddress())
                .geom(geom)
                .phoneNumber(request.getPhoneNumber())
                .build();

        branchRepository.save(branch);

        return ResponseEntity.ok(new MessageResponse("Branch " + branch.getName() + " added successfully!"));
    }

    @GetMapping("/branches")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<Branch>> getBranches(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        Service service = Service.builder()
                .business(business)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .durationMinutes(request.getDurationMinutes())
                .build();

        serviceRepository.save(service);

        return ResponseEntity.ok(new MessageResponse("Service " + service.getName() + " added successfully!"));
    }

    @GetMapping("/services")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<List<Service>> getServices(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        User staffUser = null;
        if (request.getUserId() != null) {
            staffUser = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new RuntimeException("Staff User login not found."));
        }

        Staff staff = Staff.builder()
                .branch(branch)
                .user(staffUser)
                .name(request.getName())
                .designation(request.getDesignation())
                .build();

        staffRepository.save(staff);

        return ResponseEntity.ok(new MessageResponse("Staff member " + staff.getName() + " added successfully!"));
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getAllStaff(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business;
        if (user.getRole() == UserRole.BUSINESS_OWNER) {
            business = businessRepository.findByOwner(user)
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
        if (user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = businessRepository.findByOwner(user)
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

    @Autowired
    private WorkingHourRepository workingHourRepository;

    @Autowired
    private BreakRepository breakRepository;

    @Autowired
    private HolidayRepository holidayRepository;

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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        Staff staff = null;
        if (request.getStaffId() != null) {
            staff = staffRepository.findById(request.getStaffId()).orElseThrow();
        }

        java.time.LocalTime start = request.getStartTime() != null ? java.time.LocalTime.parse(request.getStartTime()) : null;
        java.time.LocalTime end = request.getEndTime() != null ? java.time.LocalTime.parse(request.getEndTime()) : null;

        WorkingHour wh = WorkingHour.builder()
                .branch(branch)
                .staff(staff)
                .dayOfWeek(request.getDayOfWeek())
                .startTime(start)
                .endTime(end)
                .closed(request.getClosed() != null ? request.getClosed() : false)
                .build();

        workingHourRepository.save(wh);
        return ResponseEntity.ok(new MessageResponse("Working hours updated successfully!"));
    }

    @PostMapping("/working-hours/{workingHourId}/breaks")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> addBreakToWorkingHour(
            @PathVariable Long workingHourId,
            @Valid @RequestBody BreakRequest request) {
        
        WorkingHour workingHour = workingHourRepository.findById(workingHourId)
                .orElseThrow(() -> new RuntimeException("Working hours record not found."));

        Break restBreak = Break.builder()
                .workingHour(workingHour)
                .startTime(java.time.LocalTime.parse(request.getStartTime()))
                .endTime(java.time.LocalTime.parse(request.getEndTime()))
                .build();

        breakRepository.save(restBreak);
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        Staff staff = null;
        if (request.getStaffId() != null) {
            staff = staffRepository.findById(request.getStaffId()).orElseThrow();
        }

        Holiday holiday = Holiday.builder()
                .branch(branch)
                .staff(staff)
                .date(java.time.LocalDate.parse(request.getDate()))
                .description(request.getDescription())
                .build();

        holidayRepository.save(holiday);
        return ResponseEntity.ok(new MessageResponse("Holiday date registered successfully!"));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> updateBusinessProfile(
            @Valid @RequestBody BusinessUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User owner = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(owner)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        business.setName(request.getName());
        business.setDescription(request.getDescription());
        business.setLogoUrl(request.getLogoUrl());
        business.setRegistrationNumber(request.getRegistrationNumber());
        business.setGalleryUrls(request.getGalleryUrls());

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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
        }

        Point geom = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));

        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setGeom(geom);
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
            List<WorkingHour> whs = workingHourRepository.findByStaffOrderByDayOfWeekAsc(staff);
            return ResponseEntity.ok(whs);
        } else {
            List<WorkingHour> whs = workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch);
            return ResponseEntity.ok(whs);
        }
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
        Business business = businessRepository.findByOwner(user)
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
            List<Holiday> holidays = holidayRepository.findByStaffOrderByDateAsc(staff);
            return ResponseEntity.ok(holidays);
        } else {
            List<Holiday> holidays = holidayRepository.findByBranchAndStaffIsNullOrderByDateAsc(branch);
            return ResponseEntity.ok(holidays);
        }
    }

    @DeleteMapping("/working-hours/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteWorkingHour(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        WorkingHour wh = workingHourRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Working hours record not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!wh.getBranch().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        workingHourRepository.delete(wh);
        return ResponseEntity.ok(new MessageResponse("Working hour record removed successfully!"));
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteHoliday(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Holiday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday record not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!holiday.getBranch().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        holidayRepository.delete(holiday);
        return ResponseEntity.ok(new MessageResponse("Holiday date cancelled successfully!"));
    }

    @DeleteMapping("/breaks/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> deleteBreak(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Break restBreak = breakRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Break record not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!restBreak.getWorkingHour().getBranch().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        breakRepository.delete(restBreak);
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));
        return ResponseEntity.ok(servicePackageRepository.findByBusiness(business));
    }

    @PostMapping("/packages")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    public ResponseEntity<?> createPackage(
            @Valid @RequestBody PackageRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (!pkg.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!service.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!top.getService().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

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
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business profile not found."));

        if (!top.getService().getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access."));
        }

        timeOfDayPricingRepository.delete(top);
        return ResponseEntity.ok(new MessageResponse("Peak pricing rule deleted."));
    }
}
