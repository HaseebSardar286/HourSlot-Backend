package com.duemet.notification.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PushSubscriptionRepository {

    public record PushSubscription(Long id, Long userId, String endpoint, String p256dh, String auth) {
    }

    private final JdbcSupport jdbc;

    public PushSubscriptionRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Save a device; re-subscribing the same browser (same endpoint) moves it to the current user. */
    public void upsert(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
        jdbc.update("""
                INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, user_agent, created_at)
                VALUES (:userId, :endpoint, :p256dh, :auth, :userAgent, NOW())
                ON CONFLICT (endpoint) DO UPDATE SET
                    user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth,
                    user_agent = EXCLUDED.user_agent
                """, jdbc.params().addValue("userId", userId).addValue("endpoint", endpoint)
                .addValue("p256dh", p256dh).addValue("auth", auth)
                .addValue("userAgent", userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length()))));
    }

    public List<PushSubscription> findByUser(Long userId) {
        return jdbc.findList("SELECT id, user_id, endpoint, p256dh, auth FROM push_subscriptions WHERE user_id = :userId",
                jdbc.params().addValue("userId", userId),
                (rs, i) -> new PushSubscription(rs.getLong("id"), rs.getLong("user_id"), rs.getString("endpoint"),
                        rs.getString("p256dh"), rs.getString("auth")));
    }

    public int deleteForUser(Long userId, String endpoint) {
        return jdbc.update("DELETE FROM push_subscriptions WHERE user_id = :userId AND endpoint = :endpoint",
                jdbc.params().addValue("userId", userId).addValue("endpoint", endpoint));
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM push_subscriptions WHERE id = :id", jdbc.params().addValue("id", id));
    }

    public void touch(Long id) {
        jdbc.update("UPDATE push_subscriptions SET last_used_at = NOW() WHERE id = :id", jdbc.params().addValue("id", id));
    }
}
