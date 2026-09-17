package com.hourslot.community.repository;

import com.hourslot.community.model.CommunityReaction;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CommunityReactionRepository {
    private static final String SELECT = """
            SELECT id, post_id, user_id, reaction_type, created_at
            FROM community_reactions
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<CommunityReaction> mapper = (rs, i) -> CommunityReaction.builder()
            .id(rs.getLong("id"))
            .postId(rs.getLong("post_id"))
            .userId(rs.getLong("user_id"))
            .reactionType(rs.getString("reaction_type"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public CommunityReactionRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public CommunityReaction upsert(CommunityReaction reaction) {
        jdbc.update("""
                INSERT INTO community_reactions (post_id, user_id, reaction_type, created_at)
                VALUES (:postId, :userId, :reactionType, NOW())
                ON CONFLICT (post_id, user_id, reaction_type) DO NOTHING
                """, jdbc.params()
                .addValue("postId", reaction.getPostId())
                .addValue("userId", reaction.getUserId())
                .addValue("reactionType", reaction.getReactionType() == null ? "LIKE" : reaction.getReactionType()));
        return reaction;
    }

    public void remove(Long postId, Long userId, String reactionType) {
        jdbc.update("""
                DELETE FROM community_reactions
                WHERE post_id = :postId AND user_id = :userId AND reaction_type = :reactionType
                """, jdbc.params()
                .addValue("postId", postId)
                .addValue("userId", userId)
                .addValue("reactionType", reactionType == null ? "LIKE" : reactionType));
    }

    public List<CommunityReaction> findByPost(Long postId) {
        return jdbc.findList(SELECT + " WHERE post_id = :postId ORDER BY created_at",
                jdbc.params().addValue("postId", postId), mapper);
    }
}
