package com.hourslot.service;

import com.hourslot.model.Branch;
import com.hourslot.model.BranchBreak;
import com.hourslot.model.BranchHoliday;
import com.hourslot.model.BranchWorkingHour;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffBreak;
import com.hourslot.model.StaffTimeOff;
import com.hourslot.model.StaffWorkingHour;
import com.hourslot.repository.BranchHolidayRepository;
import com.hourslot.repository.BranchWorkingHourRepository;
import com.hourslot.repository.StaffTimeOffRepository;
import com.hourslot.repository.StaffWorkingHourRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
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
                StaffWorkingHour wh = staffHours.get();
                List<TimeWindow> breaks = new ArrayList<>();
                if (wh.getBreaks() != null) {
                    for (StaffBreak b : wh.getBreaks()) {
                        breaks.add(new TimeWindow(b.getStartTime(), b.getEndTime()));
                    }
                }
                return Optional.of(new DayHours(wh.getId(), "STAFF", wh.getDayOfWeek(),
                        wh.getStartTime(), wh.getEndTime(), wh.isClosed(), breaks));
            }
        }
        return branchWorkingHourRepository.findByBranchAndDayOfWeek(branch, dayOfWeek)
                .map(wh -> {
                    List<TimeWindow> breaks = new ArrayList<>();
                    if (wh.getBreaks() != null) {
                        for (BranchBreak b : wh.getBreaks()) {
                            breaks.add(new TimeWindow(b.getStartTime(), b.getEndTime()));
                        }
                    }
                    return new DayHours(wh.getId(), "BRANCH", wh.getDayOfWeek(),
                            wh.getStartTime(), wh.getEndTime(), wh.isClosed(), breaks);
                });
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

    @Data
    @AllArgsConstructor
    public static class DayHours {
        private Long id;
        private String source;
        private int dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean closed;
        private List<TimeWindow> breaks;
    }

    @Data
    @AllArgsConstructor
    public static class TimeWindow {
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
