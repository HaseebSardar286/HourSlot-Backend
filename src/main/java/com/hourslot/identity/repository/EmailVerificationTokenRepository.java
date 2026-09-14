package com.hourslot.identity.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import com.hourslot.identity.model.EmailVerificationToken;
import com.hourslot.identity.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EmailVerificationTokenRepository {

    private static final String SELECT = """
            SELECT id, user_id, token_hash, expires_at, used, created_at
            FROM email_verification_tokens
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public EmailVerificationTokenRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return jdbc.findOne(SELECT + " WHERE token_hash = :tokenHash",
                jdbc.params().addValue("tokenHash", tokenHash), rows.emailVerificationToken);
    }

    public EmailVerificationToken save(EmailVerificationToken token) {
        if (token.getId() == null) {
            token.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO email_verification_tokens (user_id, token_hash, expires_at, used, created_at)
                    VALUES (:userId, :tokenHash, :expiresAt, :used, :createdAt)
                    """, bind(token));
            token.setId(id);
            return token;
        }
        jdbc.update("""
                UPDATE email_verification_tokens SET user_id = :userId, token_hash = :tokenHash, expires_at = :expiresAt,
                    used = :used
                WHERE id = :id
                """, bind(token).addValue("id", token.getId()));
        return token;
    }

    public void deleteByUser(User user) {
        jdbc.update("DELETE FROM email_verification_tokens WHERE user_id = :userId",
                jdbc.params().addValue("userId", user == null ? null : user.getId()));
    }

    private MapSqlParameterSource bind(EmailVerificationToken token) {
        return jdbc.params()
                .addValue("userId", token.getUser() == null ? null : token.getUser().getId())
                .addValue("tokenHash", token.getTokenHash())
                .addValue("expiresAt", JdbcSupport.ts(token.getExpiresAt()))
                .addValue("used", token.isUsed())
                .addValue("createdAt", JdbcSupport.ts(token.getCreatedAt()));
    }
}
