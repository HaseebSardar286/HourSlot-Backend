package com.hourslot.web.controller;

import com.hourslot.booking.model.Booking;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.booking.model.BookingStatus;
import com.hourslot.organization.model.Branch;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.availability.repository.BranchWorkingHourRepository;
import com.hourslot.catalog.services.PricingService;
import com.hourslot.availability.services.ScheduleService;
import com.hourslot.catalog.model.Service;
import com.hourslot.catalog.repository.ServiceRepository;
import com.hourslot.availability.services.SlotLockService;
import com.hourslot.organization.model.Staff;
import com.hourslot.organization.repository.StaffRepository;
import com.hourslot.catalog.model.StaffService;
import com.hourslot.catalog.repository.StaffServiceRepository;
import com.hourslot.availability.repository.StaffWorkingHourRepository;
import com.hourslot.catalog.model.TimeOfDayPricing;
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
                    .filter(m -> m.getStaff() != null
                            && m.getStaff().getBranch() != null
                            && branchId.equals(m.getStaff().getBranch().getId()))
                    .collect(Collectors.toList());
            if (mapping.isPresent() || anyMappings.isEmpty()) {
                targetStaff.add(staff);
            }
        } else {
            List<StaffService> mappings = staffServiceRepository.findByService(service);
            for (StaffService mapping : mappings) {
                Staff member = mapping.getStaff();
                if (member != null && member.getBranch() != null && branchId.equals(member.getBranch().getId())) {
                    targetStaff.add(member);
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
            if (wh == null || wh.isClosed() || wh.getIntervals() == null || wh.getIntervals().isEmpty()) {
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

            for (ScheduleService.TimeWindow window : wh.getIntervals()) {
                if (window.getStartTime() == null || window.getEndTime() == null) continue;
                mergeSlots(uniqueSlots, generateSlotsForWindow(
                        branchId, staff, service, localDate,
                        window.getStartTime(), window.getEndTime(),
                        wh.getBreaks(), bookings, wh.getSlotStepMinutes()));
            }
        }

        return ResponseEntity.ok(new ArrayList<>(uniqueSlots.values()));
    }

    private List<PricingService.PricedSlot> generateBranchSlots(Branch branch, Service service, LocalDate localDate, int dayOfWeek) {
        if (scheduleService.isHoliday(branch, null, localDate)) {
            return Collections.emptyList();
        }
        ScheduleService.DayHours wh = scheduleService.resolveDayHours(branch, null, dayOfWeek).orElse(null);
        if (wh == null || wh.isClosed() || wh.getIntervals() == null || wh.getIntervals().isEmpty()) {
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

        List<PricingService.PricedSlot> slots = new ArrayList<>();
        Map<String, PricingService.PricedSlot> unique = new TreeMap<>();
        for (ScheduleService.TimeWindow window : wh.getIntervals()) {
            if (window.getStartTime() == null || window.getEndTime() == null) continue;
            mergeSlots(unique, generateSlotsForWindow(
                    branch.getId(), null, service, localDate,
                    window.getStartTime(), window.getEndTime(),
                    wh.getBreaks(), bookings, wh.getSlotStepMinutes()));
        }
        slots.addAll(unique.values());
        return slots;
    }

    private void mergeSlots(Map<String, PricingService.PricedSlot> into, List<PricingService.PricedSlot> incoming) {
        for (PricingService.PricedSlot slot : incoming) {
            PricingService.PricedSlot existing = into.get(slot.getStartTime());
            if (existing == null) {
                into.put(slot.getStartTime(), slot);
                continue;
            }
            if (slot.isAvailable()) {
                boolean existingWasOpen = existing.isAvailable();
                existing.setAvailable(true);
                existing.setAvailability("AVAILABLE");
                if (!existingWasOpen || slot.getPrice() < existing.getPrice()) {
                    existing.setBasePrice(slot.getBasePrice());
                    existing.setPrice(slot.getPrice());
                    existing.setPriceMultiplier(slot.getPriceMultiplier());
                    existing.setPricingKind(slot.getPricingKind());
                    existing.setPricingLabel(slot.getPricingLabel());
                    existing.setCurrency(slot.getCurrency());
                    existing.setEndTime(slot.getEndTime());
                }
                mergeStaff(existing.getAvailableStaff(), slot.getAvailableStaff());
            } else if (!existing.isAvailable()) {
                mergeStaff(existing.getAvailableStaff(), slot.getAvailableStaff());
            }
        }
    }

    private void mergeStaff(List<PricingService.SlotStaff> into, List<PricingService.SlotStaff> extra) {
        if (extra == null || extra.isEmpty()) return;
        if (into == null) return;
        Set<Long> seen = into.stream()
                .map(PricingService.SlotStaff::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (PricingService.SlotStaff member : extra) {
            if (member == null || member.getId() == null || seen.contains(member.getId())) continue;
            seen.add(member.getId());
            into.add(member);
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
            List<Booking> bookings,
            int slotStepMinutes) {

        List<PricingService.PricedSlot> slots = new ArrayList<>();
        LocalTime slotTime = shiftStart;
        int serviceDuration = service.getDurationMinutes() > 0 ? service.getDurationMinutes() : 30;
        int serviceBuffer = Math.max(0, service.getBufferMinutes());
        int step = slotStepMinutes > 0 ? slotStepMinutes : 30;
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
                slotTime = slotTime.plusMinutes(step);
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

            boolean locked = false;
            if (staff != null) {
                LocalDateTime slotDateTime = localDate.atTime(slotStart);
                String lockKey = slotLockService.buildLockKey(branchId, staff.getId(), service.getId(), slotDateTime);
                locked = slotLockService.isLocked(lockKey);
            }

            LocalDateTime startAt = localDate.atTime(slotStart);
            LocalDateTime endAt = localDate.atTime(slotEnd);
            PricingService.PriceQuote quote = pricingService.quote(service, unitPrice, dayRules, startAt, endAt);
            PricingService.PricedSlot priced = pricingService.toSlot(slotStart, slotEnd, quote);
            boolean open = !overlapsBooking && !locked;
            priced.setAvailable(open);
            priced.setAvailability(open ? "AVAILABLE" : "BOOKED");
            if (staff != null && open) {
                PricingService.SlotStaff offer = new PricingService.SlotStaff();
                offer.setId(staff.getId());
                offer.setName(staff.getName());
                priced.getAvailableStaff().add(offer);
            }
            slots.add(priced);
            slotTime = slotTime.plusMinutes(step);
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
