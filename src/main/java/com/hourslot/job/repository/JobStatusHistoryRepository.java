package com.hourslot.job.repository;

import com.hourslot.job.model.JobStatusHistory;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JobStatusHistoryRepository {
    private static final String SELECT = """
            SELECT id, job_id, from_status, to_status, changed_by, reason, created_at
            FROM job_status_history
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<JobStatusHistory> mapper = (rs, i) -> JobStatusHistory.builder()
            .id(rs.getLong("id"))
            .jobId(rs.getLong("job_id"))
            .fromStatus(rs.getString("from_status"))
            .toStatus(rs.getString("to_status"))
            .changedBy(JdbcSupport.getLong(rs, "changed_by"))
            .reason(rs.getString("reason"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public JobStatusHistoryRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public JobStatusHistory save(JobStatusHistory row) {
        row.setId(jdbc.insert("""
                INSERT INTO job_status_history (job_id, from_status, to_status, changed_by, reason, created_at)
                VALUES (:jobId, :fromStatus, :toStatus, :changedBy, :reason, :createdAt)
                """, jdbc.params()
                .addValue("jobId", row.getJobId())
                .addValue("fromStatus", row.getFromStatus())
                .addValue("toStatus", row.getToStatus())
                .addValue("changedBy", row.getChangedBy())
                .addValue("reason", row.getReason())
                .addValue("createdAt", JdbcSupport.ts(row.getCreatedAt() == null
                        ? java.time.LocalDateTime.now() : row.getCreatedAt()))));
        return row;
    }

    public List<JobStatusHistory> findByJob(Long jobId) {
        return jdbc.findList(SELECT + " WHERE job_id = :jobId ORDER BY created_at",
                jdbc.params().addValue("jobId", jobId), mapper);
    }
}
