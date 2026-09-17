package com.hourslot.job.repository;

import com.hourslot.job.model.JobNote;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JobNoteRepository {
    private static final String SELECT = """
            SELECT id, job_id, author_user_id, body, created_at
            FROM job_notes
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<JobNote> mapper = (rs, i) -> JobNote.builder()
            .id(rs.getLong("id"))
            .jobId(rs.getLong("job_id"))
            .authorUserId(rs.getLong("author_user_id"))
            .body(rs.getString("body"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public JobNoteRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public JobNote save(JobNote note) {
        note.setId(jdbc.insert("""
                INSERT INTO job_notes (job_id, author_user_id, body, created_at)
                VALUES (:jobId, :authorUserId, :body, NOW())
                """, jdbc.params()
                .addValue("jobId", note.getJobId())
                .addValue("authorUserId", note.getAuthorUserId())
                .addValue("body", note.getBody())));
        return note;
    }

    public List<JobNote> findByJob(Long jobId) {
        return jdbc.findList(SELECT + " WHERE job_id = :jobId ORDER BY created_at",
                jdbc.params().addValue("jobId", jobId), mapper);
    }
}
