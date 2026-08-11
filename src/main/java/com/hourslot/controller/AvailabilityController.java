package com.hourslot.controller;

import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.service.SlotLockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public")
public class AvailabilityController {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private WorkingHourRepository workingHourRepository;

    @Autowired
    private BreakRepository breakRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private SlotLockService slotLockService;

    @GetMapping("/branches/{branchId}/slots")
    public ResponseEntity<?> getAvailableSlots(
            @PathVariable Long branchId,
            @RequestParam Long serviceId,
            @RequestParam String date,
            @RequestParam(required = false) Long staffId) {

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));
        Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found."));

        LocalDate localDate = LocalDate.parse(date);
        int dayOfWeek = localDate.getDayOfWeek().getValue(); // 1 = Monday, 7 = Sunday

        // 1. Check if the branch is closed due to a holiday
        List<Holiday> branchHolidays = holidayRepository.findByBranchAndDate(branch, localDate);
        if (!branchHolidays.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 2. Resolve target staff list who are assigned to this service
        List<Staff> targetStaff = new ArrayList<>();
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff member not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body("Error: Specialist does not belong to this branch.");
            }
            // Check mapping
            Optional<StaffService> mapping = staffServiceRepository.findByStaffAndService(staff, service);
            if (mapping.isPresent()) {
                targetStaff.add(staff);
            }
        } else {
            // Find all staff who deliver this service at this branch
            List<StaffService> mappings = staffServiceRepository.findByService(service);
            for (StaffService mapping : mappings) {
                if (mapping.getStaff().getBranch().getId().equals(branchId)) {
                    targetStaff.add(mapping.getStaff());
                }
            }
        }

        if (targetStaff.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 3. Compute union of available slots across all eligible staff
        Set<String> uniqueSlots = new TreeSet<>(); // TreeSet keeps slots sorted automatically!

        for (Staff staff : targetStaff) {
            // Check if staff has a holiday on this day
            List<Holiday> staffHolidays = holidayRepository.findByStaffAndDate(staff, localDate);
            if (!staffHolidays.isEmpty()) {
                continue; // Skip staff member
            }

            // Resolve working hours for this staff member
            // Check staff specific schedule first
            WorkingHour wh = workingHourRepository.findByStaff(staff).stream()
                    .filter(h -> h.getDayOfWeek() == dayOfWeek)
                    .findFirst()
                    .orElse(null);

            if (wh == null) {
                // Fallback to branch-level schedule
                wh = workingHourRepository.findByBranch(branch).stream()
                        .filter(h -> h.getDayOfWeek() == dayOfWeek && h.getStaff() == null)
                        .findFirst()
                        .orElse(null);
            }

            if (wh == null || wh.isClosed()) {
                continue; // Skip staff member
            }

            LocalTime shiftStart = wh.getStartTime();
            LocalTime shiftEnd = wh.getEndTime();
            if (shiftStart == null || shiftEnd == null) {
                continue;
            }

            // Load breaks for this working hour record
            List<Break> breaks = breakRepository.findByWorkingHour(wh);

            // Load bookings for this staff member on this date (not cancelled / rejected)
            LocalDateTime startOfDay = localDate.atStartOfDay();
            LocalDateTime endOfDay = localDate.atTime(LocalTime.MAX);
            List<BookingStatus> activeStatuses = Arrays.asList(
                    BookingStatus.PENDING,
                    BookingStatus.CONFIRMED
            );
            List<Booking> bookings = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                    staff, startOfDay, endOfDay, activeStatuses
            );

            // Generate slots in 30-minute steps
            LocalTime slotTime = shiftStart;
            int serviceDuration = service.getDurationMinutes();
            int serviceBuffer = service.getBufferMinutes();

            while (slotTime.plusMinutes(serviceDuration).isBefore(shiftEnd) || 
                   slotTime.plusMinutes(serviceDuration).equals(shiftEnd)) {

                LocalTime slotStart = slotTime;
                LocalTime slotEnd = slotTime.plusMinutes(serviceDuration);

                // Check overlap with breaks
                boolean overlapsBreak = false;
                for (Break b : breaks) {
                    if (slotStart.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime())) {
                        overlapsBreak = true;
                        break;
                    }
                }

                if (overlapsBreak) {
                    slotTime = slotTime.plusMinutes(30);
                    continue;
                }

                // Check overlap with active bookings
                boolean overlapsBooking = false;
                for (Booking booking : bookings) {
                    LocalTime bookingStart = booking.getBookingTime().toLocalTime();
                    // Add buffer time to blocking window
                    LocalTime bookingEndWithBuffer = booking.getEndTime().toLocalTime().plusMinutes(serviceBuffer);
                    
                    // Slot also needs buffer time
                    LocalTime slotEndWithBuffer = slotEnd.plusMinutes(serviceBuffer);

                    if (slotStart.isBefore(bookingEndWithBuffer) && slotEndWithBuffer.isAfter(bookingStart)) {
                        overlapsBooking = true;
                        break;
                    }
                }

                if (!overlapsBooking) {
                    LocalDateTime slotDateTime = localDate.atTime(slotStart);
                    String lockKey = slotLockService.buildLockKey(
                            branchId, staff.getId(), serviceId, slotDateTime);
                    if (slotLockService.isLocked(lockKey)) {
                        slotTime = slotTime.plusMinutes(30);
                        continue;
                    }
                    String formatted = String.format("%02d:%02d", slotStart.getHour(), slotStart.getMinute());
                    uniqueSlots.add(formatted);
                }

                slotTime = slotTime.plusMinutes(30);
            }
        }

        return ResponseEntity.ok(new ArrayList<>(uniqueSlots));
    }

    @GetMapping("/branches/{branchId}/working-hours")
    public ResponseEntity<?> getPublicWorkingHours(
            @PathVariable Long branchId,
            @RequestParam(required = false) Long staffId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body("Error: Staff does not belong to this branch.");
            }
            return ResponseEntity.ok(workingHourRepository.findByStaffOrderByDayOfWeekAsc(staff));
        }
        return ResponseEntity.ok(workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch));
    }
}
