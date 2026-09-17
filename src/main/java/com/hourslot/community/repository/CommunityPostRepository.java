package com.hourslot.community.repository;

import com.hourslot.community.model.CommunityPost;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CommunityPostRepository {
    private static final String SELECT = """
            SELECT id, community_id, author_user_id, provider_business_id, category, title, body,
                   status, created_at, updated_at, deleted_at
            FROM community_posts
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<CommunityPost> mapper = (rs, i) -> CommunityPost.builder()
            .id(rs.getLong("id"))
            .communityId(rs.getLong("community_id"))
            .authorUserId(rs.getLong("author_user_id"))
            .providerBusinessId(JdbcSupport.getLong(rs, "provider_business_id"))
            .category(rs.getString("category"))
            .title(rs.getString("title"))
            .body(rs.getString("body"))
            .status(rs.getString("status"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .deletedAt(JdbcSupport.localDateTime(rs, "deleted_at"))
            .build();

    public CommunityPostRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public CommunityPost save(CommunityPost post) {
        if (post.getId() == null) {
            post.onCreate();
            post.setId(jdbc.insert("""
                    INSERT INTO community_posts (
                        community_id, author_user_id, provider_business_id, category, title, body,
                        status, created_at, updated_at)
                    VALUES (
                        :communityId, :authorUserId, :providerBusinessId, :category, :title, :body,
                        :status, :createdAt, :updatedAt)
                    """, bind(post)));
        } else {
            post.onUpdate();
            jdbc.update("""
                    UPDATE community_posts SET category = :category, title = :title, body = :body,
                        status = :status, updated_at = :updatedAt
                    WHERE id = :id AND deleted_at IS NULL
                    """, bind(post).addValue("id", post.getId()));
        }
        return post;
    }

    public Optional<CommunityPost> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), mapper);
    }

    public List<CommunityPost> findByCommunity(Long communityId) {
        return jdbc.findList(SELECT + """
                 WHERE community_id = :communityId AND deleted_at IS NULL AND status = 'PUBLISHED'
                 ORDER BY created_at DESC
                """, jdbc.params().addValue("communityId", communityId), mapper);
    }

    public void softDelete(Long id) {
        jdbc.update("""
                UPDATE community_posts SET status = 'REMOVED', deleted_at = NOW(), updated_at = NOW()
                WHERE id = :id
                """, jdbc.params().addValue("id", id));
    }

    private MapSqlParameterSource bind(CommunityPost post) {
        return jdbc.params()
                .addValue("communityId", post.getCommunityId())
                .addValue("authorUserId", post.getAuthorUserId())
                .addValue("providerBusinessId", post.getProviderBusinessId())
                .addValue("category", post.getCategory())
                .addValue("title", post.getTitle())
                .addValue("body", post.getBody())
                .addValue("status", post.getStatus())
                .addValue("createdAt", JdbcSupport.ts(post.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(post.getUpdatedAt()));
    }
}
