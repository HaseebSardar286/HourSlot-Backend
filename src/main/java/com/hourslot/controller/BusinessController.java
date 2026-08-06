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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'BUSINESS_STAFF')")
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<?> addBranch(
            @Valid @RequestBody BranchRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        if (!business.isVerified()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Your business profile is not verified yet."));
        }

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
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'BUSINESS_STAFF')")
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'BUSINESS_STAFF')")
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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

    @GetMapping("/branches/{branchId}/staff")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getStaffByBranch(
            @PathVariable Long branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        // Verify that the branch belongs to the authenticated admin's business
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business business = businessRepository.findByOwner(user)
                .orElseThrow(() -> new RuntimeException("Business not found for owner."));

        if (!branch.getBusiness().getId().equals(business.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
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
}
