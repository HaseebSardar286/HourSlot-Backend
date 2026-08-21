package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffBreak;
import com.hourslot.model.StaffWorkingHour;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class StaffWorkingHourRepository {

    private static final String SELECT = """
            SELECT id, staff_id, day_of_week, start_time, end_time, closed
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
        found.ifPresent(hour -> attachBreaks(List.of(hour)));
        return found;
    }

    public StaffWorkingHour save(StaffWorkingHour hour) {
        if (hour.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO staff_working_hours (staff_id, day_of_week, start_time, end_time, closed)
                    VALUES (:staffId, :dayOfWeek, :startTime, :endTime, :closed)
                    """, bind(hour));
            hour.setId(id);
        } else {
            jdbc.update("""
                    UPDATE staff_working_hours SET staff_id = :staffId, day_of_week = :dayOfWeek,
                        start_time = :startTime, end_time = :endTime, closed = :closed
                    WHERE id = :id
                    """, bind(hour).addValue("id", hour.getId()));
        }
        syncBreaks(hour);
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
        return hours;
    }

    public List<StaffWorkingHour> findByStaff(Staff staff) {
        List<StaffWorkingHour> hours = jdbc.findList(
                SELECT + " WHERE staff_id = :staffId ORDER BY day_of_week",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffWorkingHour);
        attachBreaks(hours);
        return hours;
    }

    public Optional<StaffWorkingHour> findByStaffAndDayOfWeek(Staff staff, int dayOfWeek) {
        Optional<StaffWorkingHour> found = jdbc.findOne(
                SELECT + " WHERE staff_id = :staffId AND day_of_week = :dayOfWeek",
                jdbc.params().addValue("staffId", staff.getId()).addValue("dayOfWeek", dayOfWeek),
                rows.staffWorkingHour);
        found.ifPresent(hour -> attachBreaks(List.of(hour)));
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

    private MapSqlParameterSource bind(StaffWorkingHour hour) {
        return jdbc.params()
                .addValue("staffId", hour.getStaff() == null ? null : hour.getStaff().getId())
                .addValue("dayOfWeek", hour.getDayOfWeek())
                .addValue("startTime", time(hour.getStartTime()))
                .addValue("endTime", time(hour.getEndTime()))
                .addValue("closed", hour.isClosed());
    }

    private MapSqlParameterSource bindBreak(StaffBreak br) {
        return jdbc.params()
                .addValue("workingHourId", br.getWorkingHour() == null ? null : br.getWorkingHour().getId())
                .addValue("startTime", time(br.getStartTime()))
                .addValue("endTime", time(br.getEndTime()));
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
