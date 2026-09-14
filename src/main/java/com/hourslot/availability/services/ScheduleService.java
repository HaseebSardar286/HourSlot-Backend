package com.hourslot.availability.services;

import com.hourslot.organization.model.Branch;
import com.hourslot.availability.model.BranchBreak;
import com.hourslot.availability.model.BranchHoliday;
import com.hourslot.availability.model.BranchWorkingHour;
import com.hourslot.availability.model.BranchWorkingInterval;
import com.hourslot.organization.model.Staff;
import com.hourslot.availability.model.StaffBreak;
import com.hourslot.availability.model.StaffTimeOff;
import com.hourslot.availability.model.StaffWorkingHour;
import com.hourslot.availability.model.StaffWorkingInterval;
import com.hourslot.availability.repository.BranchHolidayRepository;
import com.hourslot.availability.repository.BranchWorkingHourRepository;
import com.hourslot.availability.repository.StaffTimeOffRepository;
import com.hourslot.availability.repository.StaffWorkingHourRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ScheduleService {

    private final BranchWorkingHourRepository branchWorkingHourRepository;
    private final StaffWorkingHourRepository staffWorkingHourRepository;
    private final BranchHolidayRepository branchHolidayRepository;
    private final StaffTimeOffRepository staffTimeOffRepository;

    public ScheduleService(
            BranchWorkingHourRepository branchWorkingHourRepository,
            StaffWorkingHourRepository staffWorkingHourRepository,
            BranchHolidayRepository branchHolidayRepository,
            StaffTimeOffRepository staffTimeOffRepository) {
        this.branchWorkingHourRepository = branchWorkingHourRepository;
        this.staffWorkingHourRepository = staffWorkingHourRepository;
        this.branchHolidayRepository = branchHolidayRepository;
        this.staffTimeOffRepository = staffTimeOffRepository;
    }

    public Optional<DayHours> resolveDayHours(Branch branch, Staff staff, int dayOfWeek) {
        if (staff != null) {
            Optional<StaffWorkingHour> staffHours = staffWorkingHourRepository.findByStaffAndDayOfWeek(staff, dayOfWeek);
            if (staffHours.isPresent()) {
                return Optional.of(fromStaff(staffHours.get()));
            }
        }
        return branchWorkingHourRepository.findByBranchAndDayOfWeek(branch, dayOfWeek)
                .map(this::fromBranch);
    }

    public boolean isHoliday(Branch branch, Staff staff, LocalDate date) {
        List<BranchHoliday> branchHolidays = branchHolidayRepository.findByBranchAndHolidayDate(branch, date);
        if (!branchHolidays.isEmpty()) {
            return true;
        }
        if (staff == null) {
            return false;
        }
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return !staffTimeOffRepository.findOverlapping(staff, start, end).isEmpty();
    }

    public List<StaffTimeOff> findStaffTimeOff(Staff staff, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return staffTimeOffRepository.findOverlapping(staff, start, end);
    }

    private DayHours fromStaff(StaffWorkingHour wh) {
        List<TimeWindow> breaks = new ArrayList<>();
        if (wh.getBreaks() != null) {
            for (StaffBreak b : wh.getBreaks()) {
                breaks.add(new TimeWindow(b.getStartTime(), b.getEndTime()));
            }
        }
        List<TimeWindow> intervals = new ArrayList<>();
        if (wh.getIntervals() != null) {
            for (StaffWorkingInterval interval : wh.getIntervals()) {
                if (interval.getStartTime() != null && interval.getEndTime() != null) {
                    intervals.add(new TimeWindow(interval.getStartTime(), interval.getEndTime()));
                }
            }
        }
        return buildDayHours(wh.getId(), "STAFF", wh.getDayOfWeek(), wh.getStartTime(), wh.getEndTime(),
                wh.isClosed(), wh.getSlotStepMinutes(), intervals, breaks);
    }

    private DayHours fromBranch(BranchWorkingHour wh) {
        List<TimeWindow> breaks = new ArrayList<>();
        if (wh.getBreaks() != null) {
            for (BranchBreak b : wh.getBreaks()) {
                breaks.add(new TimeWindow(b.getStartTime(), b.getEndTime()));
            }
        }
        List<TimeWindow> intervals = new ArrayList<>();
        if (wh.getIntervals() != null) {
            for (BranchWorkingInterval interval : wh.getIntervals()) {
                if (interval.getStartTime() != null && interval.getEndTime() != null) {
                    intervals.add(new TimeWindow(interval.getStartTime(), interval.getEndTime()));
                }
            }
        }
        return buildDayHours(wh.getId(), "BRANCH", wh.getDayOfWeek(), wh.getStartTime(), wh.getEndTime(),
                wh.isClosed(), wh.getSlotStepMinutes(), intervals, breaks);
    }

    private DayHours buildDayHours(
            Long id,
            String source,
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            boolean closed,
            int slotStepMinutes,
            List<TimeWindow> intervals,
            List<TimeWindow> breaks) {
        List<TimeWindow> resolvedIntervals = new ArrayList<>(intervals);
        if (resolvedIntervals.isEmpty() && !closed && startTime != null && endTime != null) {
            resolvedIntervals.add(new TimeWindow(startTime, endTime));
        }
        resolvedIntervals.sort(Comparator.comparing(TimeWindow::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        LocalTime envelopeStart = startTime;
        LocalTime envelopeEnd = endTime;
        if (!resolvedIntervals.isEmpty()) {
            envelopeStart = resolvedIntervals.stream()
                    .map(TimeWindow::getStartTime)
                    .filter(t -> t != null)
                    .min(Comparator.naturalOrder())
                    .orElse(startTime);
            envelopeEnd = resolvedIntervals.stream()
                    .map(TimeWindow::getEndTime)
                    .filter(t -> t != null)
                    .max(Comparator.naturalOrder())
                    .orElse(endTime);
        }

        int step = slotStepMinutes > 0 ? slotStepMinutes : 30;
        return new DayHours(id, source, dayOfWeek, envelopeStart, envelopeEnd, closed, step, resolvedIntervals, breaks);
    }

    @Data
    @AllArgsConstructor
    public static class DayHours {
        private Long id;
        private String source;
        private int dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean closed;
        private int slotStepMinutes;
        private List<TimeWindow> intervals;
        private List<TimeWindow> breaks;
    }

    @Data
    @AllArgsConstructor
    public static class TimeWindow {
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
