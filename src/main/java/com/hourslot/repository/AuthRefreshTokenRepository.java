package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.AuthRefreshToken;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AuthRefreshTokenRepository {

    private static final String SELECT = """
            SELECT id, user_id, token_hash, expires_at, revoked_at, created_at, user_agent, ip_address
            FROM auth_refresh_tokens
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public AuthRefreshTokenRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<AuthRefreshToken> findByTokenHash(String tokenHash) {
        return jdbc.findOne(SELECT + " WHERE token_hash = :tokenHash",
                jdbc.params().addValue("tokenHash", tokenHash), rows.authRefreshToken);
    }

    public AuthRefreshToken save(AuthRefreshToken token) {
        if (token.getId() == null) {
            token.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO auth_refresh_tokens (user_id, token_hash, expires_at, revoked_at, created_at, user_agent, ip_address)
                    VALUES (:userId, :tokenHash, :expiresAt, :revokedAt, :createdAt, :userAgent, :ipAddress)
                    """, bind(token));
            token.setId(id);
            return token;
        }
        jdbc.update("""
                UPDATE auth_refresh_tokens SET user_id = :userId, token_hash = :tokenHash, expires_at = :expiresAt,
                    revoked_at = :revokedAt, user_agent = :userAgent, ip_address = :ipAddress
                WHERE id = :id
                """, bind(token).addValue("id", token.getId()));
        return token;
    }

    private MapSqlParameterSource bind(AuthRefreshToken token) {
        return jdbc.params()
                .addValue("userId", token.getUser() == null ? null : token.getUser().getId())
                .addValue("tokenHash", token.getTokenHash())
                .addValue("expiresAt", JdbcSupport.ts(token.getExpiresAt()))
                .addValue("revokedAt", JdbcSupport.ts(token.getRevokedAt()))
                .addValue("createdAt", JdbcSupport.ts(token.getCreatedAt()))
                .addValue("userAgent", token.getUserAgent())
                .addValue("ipAddress", token.getIpAddress());
    }
}
