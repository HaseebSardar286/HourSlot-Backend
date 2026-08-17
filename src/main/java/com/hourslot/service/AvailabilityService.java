package com.hourslot.service;

import lombok.Data;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class AvailabilityService {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Data
    public static class Slot {
        private String startTime;
        private String endTime;
        private boolean available;
    }

    public List<Slot> getAvailableSlots(Long branchId, Long staffId, Long serviceId, LocalDate date) {
        List<Slot> availableSlots = new ArrayList<>();

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        com.hourslot.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        Staff staff = null;
        if (staffId != null) {
            staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found"));
        }

        if (scheduleService.isHoliday(branch, staff, date)) {
            return availableSlots;
        }

        ScheduleService.DayHours workingHour = scheduleService.resolveDayHours(branch, staff, date.getDayOfWeek().getValue())
                .orElse(null);
        if (workingHour == null || workingHour.isClosed()) {
            return availableSlots;
        }

        LocalTime workStart = workingHour.getStartTime();
        LocalTime workEnd = workingHour.getEndTime();
        if (workStart == null || workEnd == null) {
            return availableSlots;
        }

        List<TimeInterval> activeIntervals = calculateActiveIntervals(workingHour, workStart, workEnd);
        List<Booking> activeBookings = getActiveBookingsForDate(branch, staff, date);

        int duration = service.getDurationMinutes();
        int stepMinutes = 30;

        for (TimeInterval interval : activeIntervals) {
            LocalTime current = interval.start;
            while (current.plusMinutes(duration).isBefore(interval.end) || current.plusMinutes(duration).equals(interval.end)) {
                LocalTime slotStart = current;
                LocalTime slotEnd = current.plusMinutes(duration);

                boolean isSlotAvailable = true;
                for (Booking booking : activeBookings) {
                    LocalTime bookingStart = booking.getBookingTime().toLocalTime();
                    LocalTime bookingEnd = booking.getEndTime().toLocalTime();
                    if (slotStart.isBefore(bookingEnd) && slotEnd.isAfter(bookingStart)) {
                        isSlotAvailable = false;
                        break;
                    }
                }

                if (isSlotAvailable && staffId != null) {
                    String lockKey = "booking:slot:" + staffId + ":" + date + ":" + slotStart;
                    Boolean isLocked = redisTemplate.hasKey(lockKey);
                    if (Boolean.TRUE.equals(isLocked)) {
                        isSlotAvailable = false;
                    }
                }

                Slot slot = new Slot();
                slot.setStartTime(slotStart.toString());
                slot.setEndTime(slotEnd.toString());
                slot.setAvailable(isSlotAvailable);
                availableSlots.add(slot);

                current = current.plusMinutes(stepMinutes);
            }
        }

        return availableSlots;
    }

    private List<TimeInterval> calculateActiveIntervals(ScheduleService.DayHours wh, LocalTime workStart, LocalTime workEnd) {
        List<TimeInterval> intervals = new ArrayList<>();
        intervals.add(new TimeInterval(workStart, workEnd));

        if (wh.getBreaks() == null) {
            return intervals;
        }
        for (ScheduleService.TimeWindow b : wh.getBreaks()) {
            List<TimeInterval> nextIntervals = new ArrayList<>();
            for (TimeInterval interval : intervals) {
                if (b.getStartTime().isBefore(interval.end) && b.getEndTime().isAfter(interval.start)) {
                    if (b.getStartTime().isAfter(interval.start)) {
                        nextIntervals.add(new TimeInterval(interval.start, b.getStartTime()));
                    }
                    if (b.getEndTime().isBefore(interval.end)) {
                        nextIntervals.add(new TimeInterval(b.getEndTime(), interval.end));
                    }
                } else {
                    nextIntervals.add(interval);
                }
            }
            intervals = nextIntervals;
        }
        return intervals;
    }

    private List<Booking> getActiveBookingsForDate(Branch branch, Staff staff, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);
        List<BookingStatus> activeStatuses = Arrays.asList(
                BookingStatus.PENDING,
                BookingStatus.CONFIRMED,
                BookingStatus.COMPLETED
        );

        if (staff != null) {
            return bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(staff, dayStart, dayEnd, activeStatuses);
        }
        return bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(branch, dayStart, dayEnd, activeStatuses);
    }

    private static class TimeInterval {
        LocalTime start;
        LocalTime end;

        TimeInterval(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
