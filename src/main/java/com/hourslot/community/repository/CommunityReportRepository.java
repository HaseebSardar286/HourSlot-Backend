package com.hourslot.community.repository;

import com.hourslot.community.model.CommunityReport;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CommunityReportRepository {
    private static final String SELECT = """
            SELECT id, post_id, comment_id, reported_by_user_id, reason, description, status,
                   reviewed_by_user_id, reviewed_at, created_at, updated_at
            FROM community_reports
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<CommunityReport> mapper = (rs, i) -> CommunityReport.builder()
            .id(rs.getLong("id"))
            .postId(JdbcSupport.getLong(rs, "post_id"))
            .commentId(JdbcSupport.getLong(rs, "comment_id"))
            .reportedByUserId(rs.getLong("reported_by_user_id"))
            .reason(rs.getString("reason"))
            .description(rs.getString("description"))
            .status(rs.getString("status"))
            .reviewedByUserId(JdbcSupport.getLong(rs, "reviewed_by_user_id"))
            .reviewedAt(JdbcSupport.localDateTime(rs, "reviewed_at"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .build();

    public CommunityReportRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public CommunityReport save(CommunityReport report) {
        if (report.getId() == null) {
            report.onCreate();
            report.setId(jdbc.insert("""
                    INSERT INTO community_reports (
                        post_id, comment_id, reported_by_user_id, reason, description, status,
                        created_at, updated_at)
                    VALUES (
                        :postId, :commentId, :reportedByUserId, :reason, :description, :status,
                        :createdAt, :updatedAt)
                    """, bind(report)));
        } else {
            report.onUpdate();
            jdbc.update("""
                    UPDATE community_reports SET status = :status, reviewed_by_user_id = :reviewedByUserId,
                        reviewed_at = :reviewedAt, updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(report).addValue("id", report.getId()));
        }
        return report;
    }

    public Optional<CommunityReport> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), mapper);
    }

    public List<CommunityReport> findByStatus(String status) {
        return jdbc.findList(SELECT + " WHERE status = :status ORDER BY created_at DESC",
                jdbc.params().addValue("status", status), mapper);
    }

    private MapSqlParameterSource bind(CommunityReport report) {
        return jdbc.params()
                .addValue("postId", report.getPostId())
                .addValue("commentId", report.getCommentId())
                .addValue("reportedByUserId", report.getReportedByUserId())
                .addValue("reason", report.getReason())
                .addValue("description", report.getDescription())
                .addValue("status", report.getStatus())
                .addValue("reviewedByUserId", report.getReviewedByUserId())
                .addValue("reviewedAt", JdbcSupport.ts(report.getReviewedAt()))
                .addValue("createdAt", JdbcSupport.ts(report.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(report.getUpdatedAt()));
    }
}
