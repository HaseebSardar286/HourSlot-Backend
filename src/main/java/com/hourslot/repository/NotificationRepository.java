package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Notification;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationRepository {

    private static final String SELECT = """
            SELECT id, user_id, channel, title, body, is_read, sent_at, created_at
            FROM notifications
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public NotificationRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Notification> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id",
                jdbc.params().addValue("id", id), rows.notification);
    }

    public Notification save(Notification notification) {
        if (notification.getId() == null) {
            notification.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO notifications (user_id, channel, title, body, is_read, sent_at, created_at)
                    VALUES (:userId, :channel, :title, :body, :read, :sentAt, :createdAt)
                    """, bind(notification));
            notification.setId(id);
            return notification;
        }
        jdbc.update("""
                UPDATE notifications SET user_id = :userId, channel = :channel, title = :title, body = :body,
                    is_read = :read, sent_at = :sentAt
                WHERE id = :id
                """, bind(notification).addValue("id", notification.getId()));
        return notification;
    }

    public List<Notification> saveAll(Iterable<Notification> notifications) {
        List<Notification> saved = new ArrayList<>();
        for (Notification notification : notifications) {
            saved.add(save(notification));
        }
        return saved;
    }

    public List<Notification> findByUserOrderByCreatedAtDesc(User user) {
        return jdbc.findList(SELECT + " WHERE user_id = :userId ORDER BY created_at DESC",
                jdbc.params().addValue("userId", user == null ? null : user.getId()), rows.notification);
    }

    public long countByUserAndReadFalse(User user) {
        return jdbc.count("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = false",
                jdbc.params().addValue("userId", user == null ? null : user.getId()));
    }

    private MapSqlParameterSource bind(Notification notification) {
        return jdbc.params()
                .addValue("userId", notification.getUser() == null ? null : notification.getUser().getId())
                .addValue("channel", notification.getChannel())
                .addValue("title", notification.getTitle())
                .addValue("body", notification.getBody())
                .addValue("read", notification.isRead())
                .addValue("sentAt", JdbcSupport.ts(notification.getSentAt()))
                .addValue("createdAt", JdbcSupport.ts(notification.getCreatedAt()));
    }
}
