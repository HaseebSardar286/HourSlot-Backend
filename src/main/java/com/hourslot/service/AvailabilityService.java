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
    private WorkingHourRepository workingHourRepository;

    @Autowired
    private BreakRepository breakRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private BookingRepository bookingRepository;

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

        // 1. Check for Holidays (branch or staff-specific)
        if (isHoliday(branch, staff, date)) {
            return availableSlots;
        }

        // 2. Fetch Working Hours
        WorkingHour workingHour = getWorkingHour(branch, staff, date.getDayOfWeek().getValue());
        if (workingHour == null || workingHour.isClosed()) {
            return availableSlots;
        }

        LocalTime workStart = workingHour.getStartTime();
        LocalTime workEnd = workingHour.getEndTime();
        if (workStart == null || workEnd == null) {
            return availableSlots;
        }

        // 3. Subtract Breaks to calculate active sub-intervals
        List<TimeInterval> activeIntervals = calculateActiveIntervals(workingHour, workStart, workEnd);

        // 4. Fetch existing Bookings for this date
        List<Booking> activeBookings = getActiveBookingsForDate(branch, staff, date);

        // 5. Slice intervals into slots and check availability
        int duration = service.getDurationMinutes();
        int stepMinutes = 30; // 30-minute intervals for slot start times

        for (TimeInterval interval : activeIntervals) {
            LocalTime current = interval.start;
            while (current.plusMinutes(duration).isBefore(interval.end) || current.plusMinutes(duration).equals(interval.end)) {
                LocalTime slotStart = current;
                LocalTime slotEnd = current.plusMinutes(duration);

                boolean isSlotAvailable = true;

                // Check overlap with bookings
                for (Booking booking : activeBookings) {
                    LocalTime bookingStart = booking.getBookingTime().toLocalTime();
                    LocalTime bookingEnd = booking.getEndTime().toLocalTime();

                    if (slotStart.isBefore(bookingEnd) && slotEnd.isAfter(bookingStart)) {
                        isSlotAvailable = false;
                        break;
                    }
                }

                // Check Redis lock
                if (isSlotAvailable && staffId != null) {
                    String lockKey = "booking:slot:" + staffId + ":" + date.toString() + ":" + slotStart.toString();
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

    private boolean isHoliday(Branch branch, Staff staff, LocalDate date) {
        if (staff != null) {
            List<Holiday> staffHolidays = holidayRepository.findByStaffAndDate(staff, date);
            if (!staffHolidays.isEmpty()) return true;
        }
        List<Holiday> branchHolidays = holidayRepository.findByBranchAndDate(branch, date);
        return !branchHolidays.isEmpty();
    }

    private WorkingHour getWorkingHour(Branch branch, Staff staff, int dayOfWeek) {
        if (staff != null) {
            List<WorkingHour> staffHours = workingHourRepository.findByStaff(staff);
            for (WorkingHour wh : staffHours) {
                if (wh.getDayOfWeek() == dayOfWeek) return wh;
            }
        }
        
        List<WorkingHour> branchHours = workingHourRepository.findByBranch(branch);
        for (WorkingHour wh : branchHours) {
            // Null staff indicates generic branch opening times
            if (wh.getStaff() == null && wh.getDayOfWeek() == dayOfWeek) {
                return wh;
            }
        }
        return null;
    }

    private List<TimeInterval> calculateActiveIntervals(WorkingHour wh, LocalTime workStart, LocalTime workEnd) {
        List<TimeInterval> intervals = new ArrayList<>();
        intervals.add(new TimeInterval(workStart, workEnd));

        List<Break> breaks = breakRepository.findByWorkingHour(wh);
        for (Break b : breaks) {
            List<TimeInterval> nextIntervals = new ArrayList<>();
            for (TimeInterval interval : intervals) {
                if (b.getStartTime().isBefore(interval.end) && b.getEndTime().isAfter(interval.start)) {
                    // Split the interval around the break
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
        } else {
            return bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(branch, dayStart, dayEnd, activeStatuses);
        }
    }

    private static class TimeInterval {
        LocalTime start;
        LocalTime end;

        TimeInterval(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }

    // Lombok annotations inside service scope
    @lombok.Data
    public static class SlotDetails {
        private String time;
        private boolean available;
    }
}
