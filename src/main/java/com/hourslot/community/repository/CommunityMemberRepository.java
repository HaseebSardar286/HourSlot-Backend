package com.hourslot.community.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

@Repository
public class CommunityMemberRepository {
    private final JdbcSupport jdbc;

    public CommunityMemberRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isMember(Long communityId, Long userId) {
        return jdbc.exists("""
                SELECT COUNT(*) FROM community_members
                WHERE community_id = :communityId AND user_id = :userId
                """, jdbc.params().addValue("communityId", communityId).addValue("userId", userId));
    }

    public void join(Long communityId, Long userId) {
        if (isMember(communityId, userId)) {
            return;
        }
        jdbc.update("""
                INSERT INTO community_members (community_id, user_id, role, joined_at)
                VALUES (:communityId, :userId, 'MEMBER', NOW())
                """, jdbc.params().addValue("communityId", communityId).addValue("userId", userId));
    }

    public void leave(Long communityId, Long userId) {
        jdbc.update("""
                DELETE FROM community_members
                WHERE community_id = :communityId AND user_id = :userId
                """, jdbc.params().addValue("communityId", communityId).addValue("userId", userId));
    }
}
