package com.hourslot.availability.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import com.hourslot.organization.model.Branch;
import com.hourslot.availability.model.BranchBreak;
import com.hourslot.availability.model.BranchWorkingHour;
import com.hourslot.availability.model.BranchWorkingInterval;
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
public class BranchWorkingHourRepository {

    private static final String SELECT = """
            SELECT id, branch_id, day_of_week, start_time, end_time, closed, slot_step_minutes
            FROM branch_working_hours
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BranchWorkingHourRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<BranchWorkingHour> findById(Long id) {
        Optional<BranchWorkingHour> found = jdbc.findOne(SELECT + " WHERE id = :id",
                jdbc.params().addValue("id", id), rows.branchWorkingHour);
        found.ifPresent(hour -> {
            attachBreaks(List.of(hour));
            attachIntervals(List.of(hour));
        });
        return found;
    }

    public BranchWorkingHour save(BranchWorkingHour hour) {
        normalizeEnvelope(hour);
        if (hour.getSlotStepMinutes() <= 0) {
            hour.setSlotStepMinutes(30);
        }
        if (hour.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO branch_working_hours (branch_id, day_of_week, start_time, end_time, closed, slot_step_minutes)
                    VALUES (:branchId, :dayOfWeek, :startTime, :endTime, :closed, :slotStepMinutes)
                    """, bind(hour));
            hour.setId(id);
        } else {
            jdbc.update("""
                    UPDATE branch_working_hours SET branch_id = :branchId, day_of_week = :dayOfWeek,
                        start_time = :startTime, end_time = :endTime, closed = :closed,
                        slot_step_minutes = :slotStepMinutes
                    WHERE id = :id
                    """, bind(hour).addValue("id", hour.getId()));
        }
        syncBreaks(hour);
        syncIntervals(hour);
        return hour;
    }

    public void delete(BranchWorkingHour hour) {
        jdbc.update("DELETE FROM branch_working_hours WHERE id = :id", jdbc.params().addValue("id", hour.getId()));
    }

    public List<BranchWorkingHour> findByBranchOrderByDayOfWeekAsc(Branch branch) {
        List<BranchWorkingHour> hours = jdbc.findList(
                SELECT + " WHERE branch_id = :branchId ORDER BY day_of_week ASC",
                jdbc.params().addValue("branchId", branch.getId()), rows.branchWorkingHour);
        attachBreaks(hours);
        attachIntervals(hours);
        return hours;
    }

    public List<BranchWorkingHour> findByBranch(Branch branch) {
        List<BranchWorkingHour> hours = jdbc.findList(
                SELECT + " WHERE branch_id = :branchId ORDER BY day_of_week",
                jdbc.params().addValue("branchId", branch.getId()), rows.branchWorkingHour);
        attachBreaks(hours);
        attachIntervals(hours);
        return hours;
    }

    public Optional<BranchWorkingHour> findByBranchAndDayOfWeek(Branch branch, int dayOfWeek) {
        Optional<BranchWorkingHour> found = jdbc.findOne(
                SELECT + " WHERE branch_id = :branchId AND day_of_week = :dayOfWeek",
                jdbc.params().addValue("branchId", branch.getId()).addValue("dayOfWeek", dayOfWeek),
                rows.branchWorkingHour);
        found.ifPresent(hour -> {
            attachBreaks(List.of(hour));
            attachIntervals(List.of(hour));
        });
        return found;
    }

    private void attachBreaks(List<BranchWorkingHour> hours) {
        if (hours == null || hours.isEmpty()) {
            return;
        }
        List<Long> ids = hours.stream().map(BranchWorkingHour::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            hours.forEach(hour -> hour.setBreaks(new ArrayList<>()));
            return;
        }
        List<BranchBreak> breaks = jdbc.findList(
                "SELECT id, working_hour_id, start_time, end_time FROM branch_breaks WHERE working_hour_id IN (:ids) ORDER BY start_time",
                jdbc.params().addValue("ids", ids), rows.branchBreak);
        Map<Long, List<BranchBreak>> byHour = new LinkedHashMap<>();
        for (BranchBreak br : breaks) {
            Long hourId = br.getWorkingHour() == null ? null : br.getWorkingHour().getId();
            byHour.computeIfAbsent(hourId, k -> new ArrayList<>()).add(br);
        }
        for (BranchWorkingHour hour : hours) {
            List<BranchBreak> hourBreaks = byHour.getOrDefault(hour.getId(), new ArrayList<>());
            for (BranchBreak br : hourBreaks) {
                br.setWorkingHour(hour);
            }
            hour.setBreaks(hourBreaks);
        }
    }

    private void attachIntervals(List<BranchWorkingHour> hours) {
        if (hours == null || hours.isEmpty()) {
            return;
        }
        List<Long> ids = hours.stream().map(BranchWorkingHour::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            hours.forEach(hour -> hour.setIntervals(new ArrayList<>()));
            return;
        }
        List<BranchWorkingInterval> intervals = jdbc.findList(
                """
                SELECT id, working_hour_id, start_time, end_time, sort_order
                FROM branch_working_intervals
                WHERE working_hour_id IN (:ids)
                ORDER BY sort_order ASC, start_time ASC
                """,
                jdbc.params().addValue("ids", ids), rows.branchWorkingInterval);
        Map<Long, List<BranchWorkingInterval>> byHour = new LinkedHashMap<>();
        for (BranchWorkingInterval interval : intervals) {
            Long hourId = interval.getWorkingHour() == null ? null : interval.getWorkingHour().getId();
            byHour.computeIfAbsent(hourId, k -> new ArrayList<>()).add(interval);
        }
        for (BranchWorkingHour hour : hours) {
            List<BranchWorkingInterval> hourIntervals = byHour.getOrDefault(hour.getId(), new ArrayList<>());
            for (BranchWorkingInterval interval : hourIntervals) {
                interval.setWorkingHour(hour);
            }
            hour.setIntervals(hourIntervals);
            // Legacy rows without intervals: synthesize from envelope.
            if (hourIntervals.isEmpty() && !hour.isClosed()
                    && hour.getStartTime() != null && hour.getEndTime() != null) {
                BranchWorkingInterval synthetic = BranchWorkingInterval.builder()
                        .workingHour(hour)
                        .startTime(hour.getStartTime())
                        .endTime(hour.getEndTime())
                        .sortOrder(0)
                        .build();
                hour.setIntervals(new ArrayList<>(List.of(synthetic)));
            }
        }
    }

    private void syncBreaks(BranchWorkingHour hour) {
        if (hour.getBreaks() == null) {
            return;
        }
        List<Long> keepIds = new ArrayList<>();
        for (BranchBreak br : hour.getBreaks()) {
            if (br == null) {
                continue;
            }
            br.setWorkingHour(hour);
            if (br.getId() == null) {
                Long id = jdbc.insert("""
                        INSERT INTO branch_breaks (working_hour_id, start_time, end_time)
                        VALUES (:workingHourId, :startTime, :endTime)
                        """, bindBreak(br));
                br.setId(id);
            } else {
                jdbc.update("""
                        UPDATE branch_breaks SET working_hour_id = :workingHourId, start_time = :startTime, end_time = :endTime
                        WHERE id = :id
                        """, bindBreak(br).addValue("id", br.getId()));
            }
            keepIds.add(br.getId());
        }
        if (keepIds.isEmpty()) {
            jdbc.update("DELETE FROM branch_breaks WHERE working_hour_id = :id",
                    jdbc.params().addValue("id", hour.getId()));
        } else {
            jdbc.update("DELETE FROM branch_breaks WHERE working_hour_id = :id AND id NOT IN (:keepIds)",
                    jdbc.params().addValue("id", hour.getId()).addValue("keepIds", keepIds));
        }
    }

    private void syncIntervals(BranchWorkingHour hour) {
        if (hour.getIntervals() == null) {
            return;
        }
        List<Long> keepIds = new ArrayList<>();
        int order = 0;
        for (BranchWorkingInterval interval : hour.getIntervals()) {
            if (interval == null || interval.getStartTime() == null || interval.getEndTime() == null) {
                continue;
            }
            interval.setWorkingHour(hour);
            interval.setSortOrder(order++);
            if (interval.getId() == null) {
                Long id = jdbc.insert("""
                        INSERT INTO branch_working_intervals (working_hour_id, start_time, end_time, sort_order)
                        VALUES (:workingHourId, :startTime, :endTime, :sortOrder)
                        """, bindInterval(interval));
                interval.setId(id);
            } else {
                jdbc.update("""
                        UPDATE branch_working_intervals
                        SET working_hour_id = :workingHourId, start_time = :startTime, end_time = :endTime, sort_order = :sortOrder
                        WHERE id = :id
                        """, bindInterval(interval).addValue("id", interval.getId()));
            }
            keepIds.add(interval.getId());
        }
        if (keepIds.isEmpty()) {
            jdbc.update("DELETE FROM branch_working_intervals WHERE working_hour_id = :id",
                    jdbc.params().addValue("id", hour.getId()));
        } else {
            jdbc.update("DELETE FROM branch_working_intervals WHERE working_hour_id = :id AND id NOT IN (:keepIds)",
                    jdbc.params().addValue("id", hour.getId()).addValue("keepIds", keepIds));
        }
    }

    private void normalizeEnvelope(BranchWorkingHour hour) {
        if (hour.isClosed() || hour.getIntervals() == null || hour.getIntervals().isEmpty()) {
            if (hour.isClosed()) {
                hour.setStartTime(null);
                hour.setEndTime(null);
            }
            return;
        }
        LocalTime min = hour.getIntervals().stream()
                .map(BranchWorkingInterval::getStartTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(hour.getStartTime());
        LocalTime max = hour.getIntervals().stream()
                .map(BranchWorkingInterval::getEndTime)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(hour.getEndTime());
        hour.setStartTime(min);
        hour.setEndTime(max);
    }

    private MapSqlParameterSource bind(BranchWorkingHour hour) {
        return jdbc.params()
                .addValue("branchId", hour.getBranch() == null ? null : hour.getBranch().getId())
                .addValue("dayOfWeek", hour.getDayOfWeek())
                .addValue("startTime", time(hour.getStartTime()))
                .addValue("endTime", time(hour.getEndTime()))
                .addValue("closed", hour.isClosed())
                .addValue("slotStepMinutes", hour.getSlotStepMinutes() > 0 ? hour.getSlotStepMinutes() : 30);
    }

    private MapSqlParameterSource bindBreak(BranchBreak br) {
        return jdbc.params()
                .addValue("workingHourId", br.getWorkingHour() == null ? null : br.getWorkingHour().getId())
                .addValue("startTime", time(br.getStartTime()))
                .addValue("endTime", time(br.getEndTime()));
    }

    private MapSqlParameterSource bindInterval(BranchWorkingInterval interval) {
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
