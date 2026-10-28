package com.duemet.identity.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class EmailLoginCodeRepository {

    /** The latest unused code for an email. */
    public record ActiveCode(Long id, String codeHash, int attempts, LocalDateTime expiresAt, LocalDateTime createdAt) {
    }

    private final JdbcSupport jdbc;

    public EmailLoginCodeRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Issue a new code; any earlier unused code for the email stops working. */
    public void issue(String email, String codeHash, LocalDateTime expiresAt) {
        jdbc.update("""
                UPDATE email_login_codes SET consumed_at = NOW()
                WHERE LOWER(email) = LOWER(:email) AND consumed_at IS NULL
                """, jdbc.params().addValue("email", email));
        jdbc.update("""
                INSERT INTO email_login_codes (email, code_hash, expires_at, created_at)
                VALUES (:email, :codeHash, :expiresAt, NOW())
                """, jdbc.params()
                .addValue("email", email)
                .addValue("codeHash", codeHash)
                .addValue("expiresAt", JdbcSupport.ts(expiresAt)));
    }

    public Optional<ActiveCode> findActive(String email) {
        return jdbc.findOne("""
                SELECT id, code_hash, attempts, expires_at, created_at
                FROM email_login_codes
                WHERE LOWER(email) = LOWER(:email) AND consumed_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """, jdbc.params().addValue("email", email), (rs, i) -> new ActiveCode(
                rs.getLong("id"),
                rs.getString("code_hash"),
                rs.getInt("attempts"),
                JdbcSupport.localDateTime(rs, "expires_at"),
                JdbcSupport.localDateTime(rs, "created_at")));
    }

    /** When the most recent code for this email was sent (used or not), for resend throttling. */
    public Optional<LocalDateTime> lastIssuedAt(String email) {
        return jdbc.findOne("""
                SELECT created_at FROM email_login_codes WHERE LOWER(email) = LOWER(:email)
                ORDER BY created_at DESC LIMIT 1
                """, jdbc.params().addValue("email", email), (rs, i) -> JdbcSupport.localDateTime(rs, "created_at"));
    }

    public void recordFailedAttempt(Long id) {
        jdbc.update("UPDATE email_login_codes SET attempts = attempts + 1 WHERE id = :id",
                jdbc.params().addValue("id", id));
    }

    /** Marks the code used; returns false if another request consumed it first. */
    public boolean consume(Long id) {
        return jdbc.update("UPDATE email_login_codes SET consumed_at = NOW() WHERE id = :id AND consumed_at IS NULL",
                jdbc.params().addValue("id", id)) == 1;
    }
}
