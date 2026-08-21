package com.hourslot.controller;

import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.service.PricingService;
import com.hourslot.service.ScheduleService;
import com.hourslot.service.SlotLockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    private BookingRepository bookingRepository;

    @Autowired
    private SlotLockService slotLockService;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private BranchWorkingHourRepository branchWorkingHourRepository;

    @Autowired
    private StaffWorkingHourRepository staffWorkingHourRepository;

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
        int dayOfWeek = localDate.getDayOfWeek().getValue();

        if (scheduleService.isHoliday(branch, null, localDate)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Staff> targetStaff = new ArrayList<>();
        if (staffId != null) {
            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff member not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body("Error: Specialist does not belong to this branch.");
            }
            Optional<StaffService> mapping = staffServiceRepository.findByStaffAndService(staff, service);
            List<StaffService> anyMappings = staffServiceRepository.findByService(service).stream()
                    .filter(m -> m.getStaff().getBranch().getId().equals(branchId))
                    .collect(Collectors.toList());
            if (mapping.isPresent() || anyMappings.isEmpty()) {
                targetStaff.add(staff);
            }
        } else {
            List<StaffService> mappings = staffServiceRepository.findByService(service);
            for (StaffService mapping : mappings) {
                if (mapping.getStaff().getBranch().getId().equals(branchId)) {
                    targetStaff.add(mapping.getStaff());
                }
            }
            if (targetStaff.isEmpty()) {
                targetStaff.addAll(staffRepository.findByBranch(branch));
            }
        }

        if (targetStaff.isEmpty()) {
            return ResponseEntity.ok(generateBranchSlots(branch, service, localDate, dayOfWeek));
        }

        Map<String, PricingService.PricedSlot> uniqueSlots = new TreeMap<>();
        for (Staff staff : targetStaff) {
            if (scheduleService.isHoliday(branch, staff, localDate)) {
                continue;
            }
            ScheduleService.DayHours wh = scheduleService.resolveDayHours(branch, staff, dayOfWeek).orElse(null);
            if (wh == null || wh.isClosed() || wh.getStartTime() == null || wh.getEndTime() == null) {
                continue;
            }

            LocalDateTime startOfDay = localDate.atStartOfDay();
            LocalDateTime endOfDay = localDate.atTime(LocalTime.MAX);
            List<BookingStatus> activeStatuses = Arrays.asList(
                    BookingStatus.PENDING,
                    BookingStatus.CONFIRMED,
                    BookingStatus.IN_PROGRESS
            );
            List<Booking> bookings = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                    staff, startOfDay, endOfDay, activeStatuses
            );

            mergeSlots(uniqueSlots, generateSlotsForWindow(
                    branchId, staff, service, localDate, wh.getStartTime(), wh.getEndTime(), wh.getBreaks(), bookings));
        }

        return ResponseEntity.ok(new ArrayList<>(uniqueSlots.values()));
    }

    private List<PricingService.PricedSlot> generateBranchSlots(Branch branch, Service service, LocalDate localDate, int dayOfWeek) {
        if (scheduleService.isHoliday(branch, null, localDate)) {
            return Collections.emptyList();
        }
        ScheduleService.DayHours wh = scheduleService.resolveDayHours(branch, null, dayOfWeek).orElse(null);
        if (wh == null || wh.isClosed() || wh.getStartTime() == null || wh.getEndTime() == null) {
            return Collections.emptyList();
        }

        LocalDateTime startOfDay = localDate.atStartOfDay();
        LocalDateTime endOfDay = localDate.atTime(LocalTime.MAX);
        List<BookingStatus> activeStatuses = Arrays.asList(
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS
        );
        List<Booking> bookings = bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(
                branch, startOfDay, endOfDay, activeStatuses
        );

        return generateSlotsForWindow(
                branch.getId(), null, service, localDate, wh.getStartTime(), wh.getEndTime(), wh.getBreaks(), bookings);
    }

    private void mergeSlots(Map<String, PricingService.PricedSlot> into, List<PricingService.PricedSlot> incoming) {
        for (PricingService.PricedSlot slot : incoming) {
            PricingService.PricedSlot existing = into.get(slot.getStartTime());
            if (existing == null || slot.getPrice() < existing.getPrice()) {
                into.put(slot.getStartTime(), slot);
            }
        }
    }

    private List<PricingService.PricedSlot> generateSlotsForWindow(
            Long branchId,
            Staff staff,
            Service service,
            LocalDate localDate,
            LocalTime shiftStart,
            LocalTime shiftEnd,
            List<ScheduleService.TimeWindow> breaks,
            List<Booking> bookings) {

        List<PricingService.PricedSlot> slots = new ArrayList<>();
        LocalTime slotTime = shiftStart;
        int serviceDuration = service.getDurationMinutes() > 0 ? service.getDurationMinutes() : 30;
        int serviceBuffer = Math.max(0, service.getBufferMinutes());
        List<TimeOfDayPricing> dayRules = pricingService.rulesFor(service, localDate.getDayOfWeek().getValue());
        BigDecimal unitPrice = pricingService.unitPrice(service, staff);

        while (!slotTime.plusMinutes(serviceDuration).isAfter(shiftEnd)) {
            LocalTime slotStart = slotTime;
            LocalTime slotEnd = slotTime.plusMinutes(serviceDuration);

            boolean overlapsBreak = false;
            if (breaks != null) {
                for (ScheduleService.TimeWindow b : breaks) {
                    if (b.getStartTime() == null || b.getEndTime() == null) continue;
                    if (slotStart.isBefore(b.getEndTime()) && slotEnd.isAfter(b.getStartTime())) {
                        overlapsBreak = true;
                        break;
                    }
                }
            }
            if (overlapsBreak) {
                slotTime = slotTime.plusMinutes(30);
                continue;
            }

            boolean overlapsBooking = false;
            for (Booking booking : bookings) {
                LocalTime bookingStart = booking.getBookingTime().toLocalTime();
                LocalTime bookingEndWithBuffer = booking.getEndTime().toLocalTime().plusMinutes(serviceBuffer);
                LocalTime slotEndWithBuffer = slotEnd.plusMinutes(serviceBuffer);
                if (slotStart.isBefore(bookingEndWithBuffer) && slotEndWithBuffer.isAfter(bookingStart)) {
                    overlapsBooking = true;
                    break;
                }
            }
            if (overlapsBooking) {
                slotTime = slotTime.plusMinutes(30);
                continue;
            }

            if (staff != null) {
                LocalDateTime slotDateTime = localDate.atTime(slotStart);
                String lockKey = slotLockService.buildLockKey(branchId, staff.getId(), service.getId(), slotDateTime);
                if (slotLockService.isLocked(lockKey)) {
                    slotTime = slotTime.plusMinutes(30);
                    continue;
                }
            }

            LocalDateTime startAt = localDate.atTime(slotStart);
            LocalDateTime endAt = localDate.atTime(slotEnd);
            PricingService.PriceQuote quote = pricingService.quote(service, unitPrice, dayRules, startAt, endAt);
            slots.add(pricingService.toSlot(slotStart, slotEnd, quote));
            slotTime = slotTime.plusMinutes(30);
        }

        return slots;
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
            return ResponseEntity.ok(staffWorkingHourRepository.findByStaffOrderByDayOfWeekAsc(staff));
        }
        return ResponseEntity.ok(branchWorkingHourRepository.findByBranchOrderByDayOfWeekAsc(branch));
    }
}
