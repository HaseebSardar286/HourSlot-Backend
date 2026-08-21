package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.PasswordResetToken;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PasswordResetTokenRepository {

    private static final String SELECT = """
            SELECT id, user_id, token_hash, expires_at, used, created_at
            FROM password_reset_tokens
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public PasswordResetTokenRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jdbc.findOne(SELECT + " WHERE token_hash = :tokenHash",
                jdbc.params().addValue("tokenHash", tokenHash), rows.passwordResetToken);
    }

    public PasswordResetToken save(PasswordResetToken token) {
        if (token.getId() == null) {
            token.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used, created_at)
                    VALUES (:userId, :tokenHash, :expiresAt, :used, :createdAt)
                    """, bind(token));
            token.setId(id);
            return token;
        }
        jdbc.update("""
                UPDATE password_reset_tokens SET user_id = :userId, token_hash = :tokenHash, expires_at = :expiresAt,
                    used = :used
                WHERE id = :id
                """, bind(token).addValue("id", token.getId()));
        return token;
    }

    public void deleteByUser(User user) {
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = :userId",
                jdbc.params().addValue("userId", user == null ? null : user.getId()));
    }

    private MapSqlParameterSource bind(PasswordResetToken token) {
        return jdbc.params()
                .addValue("userId", token.getUser() == null ? null : token.getUser().getId())
                .addValue("tokenHash", token.getTokenHash())
                .addValue("expiresAt", JdbcSupport.ts(token.getExpiresAt()))
                .addValue("used", token.isUsed())
                .addValue("createdAt", JdbcSupport.ts(token.getCreatedAt()));
    }
}
