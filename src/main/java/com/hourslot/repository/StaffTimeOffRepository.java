package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffTimeOff;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StaffTimeOffRepository {

    private static final String SELECT = """
            SELECT id, staff_id, start_at, end_at, reason, status
            FROM staff_time_off
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffTimeOffRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<StaffTimeOff> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.staffTimeOff);
    }

    public StaffTimeOff save(StaffTimeOff timeOff) {
        if (timeOff.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO staff_time_off (staff_id, start_at, end_at, reason, status)
                    VALUES (:staffId, :startAt, :endAt, :reason, :status)
                    """, bind(timeOff));
            timeOff.setId(id);
            return timeOff;
        }
        jdbc.update("""
                UPDATE staff_time_off SET staff_id = :staffId, start_at = :startAt, end_at = :endAt,
                    reason = :reason, status = :status
                WHERE id = :id
                """, bind(timeOff).addValue("id", timeOff.getId()));
        return timeOff;
    }

    public void delete(StaffTimeOff timeOff) {
        jdbc.update("DELETE FROM staff_time_off WHERE id = :id", jdbc.params().addValue("id", timeOff.getId()));
    }

    public List<StaffTimeOff> findByStaffOrderByStartAtAsc(Staff staff) {
        return jdbc.findList(SELECT + " WHERE staff_id = :staffId ORDER BY start_at ASC",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffTimeOff);
    }

    public List<StaffTimeOff> findOverlapping(Staff staff, LocalDateTime startAt, LocalDateTime endAt) {
        return jdbc.findList("""
                SELECT id, staff_id, start_at, end_at, reason, status
                FROM staff_time_off
                WHERE staff_id = :staffId
                  AND status = 'APPROVED'
                  AND start_at < :endAt
                  AND end_at > :startAt
                """, jdbc.params()
                .addValue("staffId", staff.getId())
                .addValue("startAt", JdbcSupport.ts(startAt))
                .addValue("endAt", JdbcSupport.ts(endAt)), rows.staffTimeOff);
    }

    private MapSqlParameterSource bind(StaffTimeOff timeOff) {
        return jdbc.params()
                .addValue("staffId", timeOff.getStaff() == null ? null : timeOff.getStaff().getId())
                .addValue("startAt", JdbcSupport.ts(timeOff.getStartAt()))
                .addValue("endAt", JdbcSupport.ts(timeOff.getEndAt()))
                .addValue("reason", timeOff.getReason())
                .addValue("status", timeOff.getStatus());
    }
}
