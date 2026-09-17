package com.hourslot.community.repository;

import com.hourslot.community.model.CommunityComment;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CommunityCommentRepository {
    private static final String SELECT = """
            SELECT id, post_id, author_user_id, body, status, created_at, updated_at, deleted_at
            FROM community_comments
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<CommunityComment> mapper = (rs, i) -> CommunityComment.builder()
            .id(rs.getLong("id"))
            .postId(rs.getLong("post_id"))
            .authorUserId(rs.getLong("author_user_id"))
            .body(rs.getString("body"))
            .status(rs.getString("status"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .deletedAt(JdbcSupport.localDateTime(rs, "deleted_at"))
            .build();

    public CommunityCommentRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public CommunityComment save(CommunityComment comment) {
        comment.onCreate();
        comment.setId(jdbc.insert("""
                INSERT INTO community_comments (post_id, author_user_id, body, status, created_at, updated_at)
                VALUES (:postId, :authorUserId, :body, :status, :createdAt, :updatedAt)
                """, jdbc.params()
                .addValue("postId", comment.getPostId())
                .addValue("authorUserId", comment.getAuthorUserId())
                .addValue("body", comment.getBody())
                .addValue("status", comment.getStatus())
                .addValue("createdAt", JdbcSupport.ts(comment.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(comment.getUpdatedAt()))));
        return comment;
    }

    public Optional<CommunityComment> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), mapper);
    }

    public List<CommunityComment> findByPost(Long postId) {
        return jdbc.findList(SELECT + """
                 WHERE post_id = :postId AND deleted_at IS NULL AND status = 'PUBLISHED'
                 ORDER BY created_at
                """, jdbc.params().addValue("postId", postId), mapper);
    }

    public void softDelete(Long id) {
        jdbc.update("""
                UPDATE community_comments SET status = 'REMOVED', deleted_at = NOW(), updated_at = NOW()
                WHERE id = :id
                """, jdbc.params().addValue("id", id));
    }
}
