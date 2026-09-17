package com.hourslot.job.repository;

import com.hourslot.job.model.Dispute;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class DisputeRepository {
    private static final String SELECT = """
            SELECT id, job_id, opened_by_user_id, reason, status, resolution_notes, created_at, updated_at
            FROM disputes
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<Dispute> mapper = (rs, i) -> Dispute.builder()
            .id(rs.getLong("id"))
            .jobId(rs.getLong("job_id"))
            .openedByUserId(rs.getLong("opened_by_user_id"))
            .reason(rs.getString("reason"))
            .status(rs.getString("status"))
            .resolutionNotes(rs.getString("resolution_notes"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .build();

    public DisputeRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Dispute save(Dispute dispute) {
        if (dispute.getId() == null) {
            dispute.onCreate();
            dispute.setId(jdbc.insert("""
                    INSERT INTO disputes (job_id, opened_by_user_id, reason, status, resolution_notes, created_at, updated_at)
                    VALUES (:jobId, :openedByUserId, :reason, :status, :resolutionNotes, :createdAt, :updatedAt)
                    """, bind(dispute)));
        } else {
            dispute.onUpdate();
            jdbc.update("""
                    UPDATE disputes SET status = :status, resolution_notes = :resolutionNotes, updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(dispute).addValue("id", dispute.getId()));
        }
        return dispute;
    }

    public Optional<Dispute> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), mapper);
    }

    public List<Dispute> findByJob(Long jobId) {
        return jdbc.findList(SELECT + " WHERE job_id = :jobId ORDER BY created_at DESC",
                jdbc.params().addValue("jobId", jobId), mapper);
    }

    public List<Dispute> findByStatus(String status) {
        return jdbc.findList(SELECT + " WHERE status = :status ORDER BY created_at DESC",
                jdbc.params().addValue("status", status), mapper);
    }

    private org.springframework.jdbc.core.namedparam.MapSqlParameterSource bind(Dispute dispute) {
        return jdbc.params()
                .addValue("jobId", dispute.getJobId())
                .addValue("openedByUserId", dispute.getOpenedByUserId())
                .addValue("reason", dispute.getReason())
                .addValue("status", dispute.getStatus())
                .addValue("resolutionNotes", dispute.getResolutionNotes())
                .addValue("createdAt", JdbcSupport.ts(dispute.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(dispute.getUpdatedAt()));
    }
}
