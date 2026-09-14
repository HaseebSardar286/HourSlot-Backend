package com.hourslot.availability.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import com.hourslot.organization.model.Staff;
import com.hourslot.availability.model.StaffBreak;
import com.hourslot.availability.model.StaffWorkingHour;
import com.hourslot.availability.model.StaffWorkingInterval;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class StaffWorkingHourRepository {

    private static final String SELECT = """
            SELECT id, staff_id, day_of_week, start_time, end_time, closed, slot_step_minutes
            FROM staff_working_hours
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffWorkingHourRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<StaffWorkingHour> findById(Long id) {
        Optional<StaffWorkingHour> found = jdbc.findOne(SELECT + " WHERE id = :id",
                jdbc.params().addValue("id", id), rows.staffWorkingHour);
        found.ifPresent(hour -> {
            attachBreaks(List.of(hour));
            attachIntervals(List.of(hour));
        });
        return found;
    }

    public StaffWorkingHour save(StaffWorkingHour hour) {
        normalizeEnvelope(hour);
        if (hour.getSlotStepMinutes() <= 0) {
            hour.setSlotStepMinutes(30);
        }
        if (hour.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO staff_working_hours (staff_id, day_of_week, start_time, end_time, closed, slot_step_minutes)
                    VALUES (:staffId, :dayOfWeek, :startTime, :endTime, :closed, :slotStepMinutes)
                    """, bind(hour));
            hour.setId(id);
        } else {
            jdbc.update("""
                    UPDATE staff_working_hours SET staff_id = :staffId, day_of_week = :dayOfWeek,
                        start_time = :startTime, end_time = :endTime, closed = :closed,
                        slot_step_minutes = :slotStepMinutes
                    WHERE id = :id
                    """, bind(hour).addValue("id", hour.getId()));
        }
        syncBreaks(hour);
        syncIntervals(hour);
        return hour;
    }

    public void delete(StaffWorkingHour hour) {
        jdbc.update("DELETE FROM staff_working_hours WHERE id = :id", jdbc.params().addValue("id", hour.getId()));
    }

    public List<StaffWorkingHour> findByStaffOrderByDayOfWeekAsc(Staff staff) {
        List<StaffWorkingHour> hours = jdbc.findList(
                SELECT + " WHERE staff_id = :staffId ORDER BY day_of_week ASC",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffWorkingHour);
        attachBreaks(hours);
        attachIntervals(hours);
        return hours;
    }

    public List<StaffWorkingHour> findByStaff(Staff staff) {
        List<StaffWorkingHour> hours = jdbc.findList(
                SELECT + " WHERE staff_id = :staffId ORDER BY day_of_week",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffWorkingHour);
        attachBreaks(hours);
        attachIntervals(hours);
        return hours;
    }

    public Optional<StaffWorkingHour> findByStaffAndDayOfWeek(Staff staff, int dayOfWeek) {
        Optional<StaffWorkingHour> found = jdbc.findOne(
                SELECT + " WHERE staff_id = :staffId AND day_of_week = :dayOfWeek",
                jdbc.params().addValue("staffId", staff.getId()).addValue("dayOfWeek", dayOfWeek),
                rows.staffWorkingHour);
        found.ifPresent(hour -> {
            attachBreaks(List.of(hour));
            attachIntervals(List.of(hour));
        });
        return found;
    }

    private void attachBreaks(List<StaffWorkingHour> hours) {
        if (hours == null || hours.isEmpty()) {
            return;
        }
        List<Long> ids = hours.stream().map(StaffWorkingHour::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            hours.forEach(hour -> hour.setBreaks(new ArrayList<>()));
            return;
        }
        List<StaffBreak> breaks = jdbc.findList(
                "SELECT id, working_hour_id, start_time, end_time FROM staff_breaks WHERE working_hour_id IN (:ids) ORDER BY start_time",
                jdbc.params().addValue("ids", ids), rows.staffBreak);
        Map<Long, List<StaffBreak>> byHour = new LinkedHashMap<>();
        for (StaffBreak br : breaks) {
            Long hourId = br.getWorkingHour() == null ? null : br.getWorkingHour().getId();
            byHour.computeIfAbsent(hourId, k -> new ArrayList<>()).add(br);
        }
        for (StaffWorkingHour hour : hours) {
            List<StaffBreak> hourBreaks = byHour.getOrDefault(hour.getId(), new ArrayList<>());
            for (StaffBreak br : hourBreaks) {
                br.setWorkingHour(hour);
            }
            hour.setBreaks(hourBreaks);
        }
    }

    private void attachIntervals(List<StaffWorkingHour> hours) {
        if (hours == null || hours.isEmpty()) {
            return;
        }
        List<Long> ids = hours.stream().map(StaffWorkingHour::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            hours.forEach(hour -> hour.setIntervals(new ArrayList<>()));
            return;
        }
        List<StaffWorkingInterval> intervals = jdbc.findList(
                """
                SELECT id, working_hour_id, start_time, end_time, sort_order
                FROM staff_working_intervals
                WHERE working_hour_id IN (:ids)
                ORDER BY sort_order ASC, start_time ASC
                """,
                jdbc.params().addValue("ids", ids), rows.staffWorkingInterval);
        Map<Long, List<StaffWorkingInterval>> byHour = new LinkedHashMap<>();
        for (StaffWorkingInterval interval : intervals) {
            Long hourId = interval.getWorkingHour() == null ? null : interval.getWorkingHour().getId();
            byHour.computeIfAbsent(hourId, k -> new ArrayList<>()).add(interval);
        }
        for (StaffWorkingHour hour : hours) {
            List<StaffWorkingInterval> hourIntervals = byHour.getOrDefault(hour.getId(), new ArrayList<>());
            for (StaffWorkingInterval interval : hourIntervals) {
                interval.setWorkingHour(hour);
            }
            hour.setIntervals(hourIntervals);
            if (hourIntervals.isEmpty() && !hour.isClosed()
                    && hour.getStartTime() != null && hour.getEndTime() != null) {
                StaffWorkingInterval synthetic = StaffWorkingInterval.builder()
                        .workingHour(hour)
                        .startTime(hour.getStartTime())
                        .endTime(hour.getEndTime())
                        .sortOrder(0)
                        .build();
                hour.setIntervals(new ArrayList<>(List.of(synthetic)));
            }
        }
    }

    private void syncBreaks(StaffWorkingHour hour) {
        if (hour.getBreaks() == null) {
            return;
        }
        List<Long> keepIds = new ArrayList<>();
        for (StaffBreak br : hour.getBreaks()) {
            if (br == null) {
                continue;
            }
            br.setWorkingHour(hour);
            if (br.getId() == null) {
                Long id = jdbc.insert("""
                        INSERT INTO staff_breaks (working_hour_id, start_time, end_time)
                        VALUES (:workingHourId, :startTime, :endTime)
                        """, bindBreak(br));
                br.setId(id);
            } else {
                jdbc.update("""
                        UPDATE staff_breaks SET working_hour_id = :workingHourId, start_time = :startTime, end_time = :endTime
                        WHERE id = :id
                        """, bindBreak(br).addValue("id", br.getId()));
            }
            keepIds.add(br.getId());
        }
        if (keepIds.isEmpty()) {
            jdbc.update("DELETE FROM staff_breaks WHERE working_hour_id = :id",
                    jdbc.params().addValue("id", hour.getId()));
        } else {
            jdbc.update("DELETE FROM staff_breaks WHERE working_hour_id = :id AND id NOT IN (:keepIds)",
                    jdbc.params().addValue("id", hour.getId()).addValue("keepIds", keepIds));
        }
    }

    private void syncIntervals(StaffWorkingHour hour) {
        if (hour.getIntervals() == null) {
            return;
        }
        List<Long> keepIds = new ArrayList<>();
        int order = 0;
        for (StaffWorkingInterval interval : hour.getIntervals()) {
            if (interval == null || interval.getStartTime() == null || interval.getEndTime() == null) {
                continue;
            }
            interval.setWorkingHour(hour);
            interval.setSortOrder(order++);
            if (interval.getId() == null) {
                Long id = jdbc.insert("""
                        INSERT INTO staff_working_intervals (working_hour_id, start_time, end_time, sort_order)
                        VALUES (:workingHourId, :startTime, :endTime, :sortOrder)
                        """, bindInterval(interval));
                interval.setId(id);
            } else {
                jdbc.update("""
                        UPDATE staff_working_intervals
                        SET working_hour_id = :workingHourId, start_time = :startTime, end_time = :endTime, sort_order = :sortOrder
                        WHERE id = :id
                        """, bindInterval(interval).addValue("id", interval.getId()));
            }
            keepIds.add(interval.getId());
        }
        if (keepIds.isEmpty()) {
            jdbc.update("DELETE FROM staff_working_intervals WHERE working_hour_id = :id",
                    jdbc.params().addValue("id", hour.getId()));
        } else {
            jdbc.update("DELETE FROM staff_working_intervals WHERE working_hour_id = :id AND id NOT IN (:keepIds)",
                    jdbc.params().addValue("id", hour.getId()).addValue("keepIds", keepIds));
        }
    }

    private void normalizeEnvelope(StaffWorkingHour hour) {
        if (hour.isClosed() || hour.getIntervals() == null || hour.getIntervals().isEmpty()) {
            if (hour.isClosed()) {
                hour.setStartTime(null);
                hour.setEndTime(null);
            }
            return;
        }
        LocalTime min = hour.getIntervals().stream()
                .map(StaffWorkingInterval::getStartTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(hour.getStartTime());
        LocalTime max = hour.getIntervals().stream()
                .map(StaffWorkingInterval::getEndTime)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(hour.getEndTime());
        hour.setStartTime(min);
        hour.setEndTime(max);
    }

    private MapSqlParameterSource bind(StaffWorkingHour hour) {
        return jdbc.params()
                .addValue("staffId", hour.getStaff() == null ? null : hour.getStaff().getId())
                .addValue("dayOfWeek", hour.getDayOfWeek())
                .addValue("startTime", time(hour.getStartTime()))
                .addValue("endTime", time(hour.getEndTime()))
                .addValue("closed", hour.isClosed())
                .addValue("slotStepMinutes", hour.getSlotStepMinutes() > 0 ? hour.getSlotStepMinutes() : 30);
    }

    private MapSqlParameterSource bindBreak(StaffBreak br) {
        return jdbc.params()
                .addValue("workingHourId", br.getWorkingHour() == null ? null : br.getWorkingHour().getId())
                .addValue("startTime", time(br.getStartTime()))
                .addValue("endTime", time(br.getEndTime()));
    }

    private MapSqlParameterSource bindInterval(StaffWorkingInterval interval) {
        return jdbc.params()
                .addValue("workingHourId", interval.getWorkingHour() == null ? null : interval.getWorkingHour().getId())
                .addValue("startTime", time(interval.getStartTime()))
                .addValue("endTime", time(interval.getEndTime()))
                .addValue("sortOrder", interval.getSortOrder());
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
