package com.hourslot.notification.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.notification.model.NotificationPreference;
import com.hourslot.identity.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class NotificationPreferenceRepository {

    private static final String SELECT = """
            SELECT user_id, channel, event_type, enabled
            FROM notification_preferences
            """;

    private final JdbcSupport jdbc;

    public NotificationPreferenceRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationPreference> findByUser(User user) {
        return findByUserId(user == null ? null : user.getId());
    }

    public List<NotificationPreference> findByUserId(Long userId) {
        return jdbc.findList(SELECT + " WHERE user_id = :userId",
                jdbc.params().addValue("userId", userId),
                (rs, rowNum) -> NotificationPreference.builder()
                        .user(User.builder().id(rs.getLong("user_id")).build())
                        .channel(rs.getString("channel"))
                        .eventType(rs.getString("event_type"))
                        .enabled(rs.getBoolean("enabled"))
                        .build());
    }

    public void upsert(User user, String channel, String eventType, boolean enabled) {
        jdbc.update("""
                INSERT INTO notification_preferences (user_id, channel, event_type, enabled)
                VALUES (:userId, :channel, :eventType, :enabled)
                ON CONFLICT (user_id, channel, event_type)
                DO UPDATE SET enabled = EXCLUDED.enabled
                """, jdbc.params()
                .addValue("userId", user.getId())
                .addValue("channel", channel)
                .addValue("eventType", eventType)
                .addValue("enabled", enabled));
    }

    public void seedDefaults(User user) {
        upsert(user, "EMAIL", "BOOKING", true);
        upsert(user, "SMS", "REMINDER", true);
        upsert(user, "EMAIL", "MARKETING", false);
    }
}
