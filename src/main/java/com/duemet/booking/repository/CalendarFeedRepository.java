package com.duemet.booking.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CalendarFeedRepository {

    private final JdbcSupport jdbc;

    public CalendarFeedRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> tokenFor(Long businessId) {
        List<String> tokens = jdbc.jdbc().queryForList(
                "SELECT token FROM calendar_feeds WHERE business_id = :id",
                jdbc.params().addValue("id", businessId), String.class);
        return tokens.stream().findFirst();
    }

    /** Create or replace the feed token (replacing invalidates any previously shared URL). */
    public void setToken(Long businessId, String token) {
        jdbc.update("""
                INSERT INTO calendar_feeds (business_id, token, created_at) VALUES (:id, :token, NOW())
                ON CONFLICT (business_id) DO UPDATE SET token = EXCLUDED.token, created_at = NOW()
                """, jdbc.params().addValue("id", businessId).addValue("token", token));
    }

    public Optional<Long> businessForToken(String token) {
        List<Long> ids = jdbc.jdbc().queryForList(
                "SELECT business_id FROM calendar_feeds WHERE token = :token",
                jdbc.params().addValue("token", token), Long.class);
        return ids.stream().findFirst();
    }
}
