package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.BranchBreak;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.Optional;

@Repository
public class BranchBreakRepository {

    private static final String SELECT = """
            SELECT id, working_hour_id, start_time, end_time
            FROM branch_breaks
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BranchBreakRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<BranchBreak> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.branchBreak);
    }

    public BranchBreak save(BranchBreak restBreak) {
        if (restBreak.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO branch_breaks (working_hour_id, start_time, end_time)
                    VALUES (:workingHourId, :startTime, :endTime)
                    """, bind(restBreak));
            restBreak.setId(id);
            return restBreak;
        }
        jdbc.update("""
                UPDATE branch_breaks SET working_hour_id = :workingHourId, start_time = :startTime, end_time = :endTime
                WHERE id = :id
                """, bind(restBreak).addValue("id", restBreak.getId()));
        return restBreak;
    }

    public void delete(BranchBreak restBreak) {
        jdbc.update("DELETE FROM branch_breaks WHERE id = :id", jdbc.params().addValue("id", restBreak.getId()));
    }

    private MapSqlParameterSource bind(BranchBreak restBreak) {
        return jdbc.params()
                .addValue("workingHourId", restBreak.getWorkingHour() == null ? null : restBreak.getWorkingHour().getId())
                .addValue("startTime", time(restBreak.getStartTime()))
                .addValue("endTime", time(restBreak.getEndTime()));
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
