package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final String SELECT = """
            SELECT id, email, password_hash, phone_number, status, email_verified_at, locale, timezone,
                   last_login_at, first_name, last_name, created_at, updated_at, deleted_at
            FROM users
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public UserRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<User> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.user);
    }

    public User getReferenceById(Long id) {
        return findById(id).orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.findOne(SELECT + " WHERE lower(email) = lower(:email) AND deleted_at IS NULL",
                jdbc.params().addValue("email", email), rows.user);
    }

    public boolean existsByEmail(String email) {
        return jdbc.exists("SELECT COUNT(*) FROM users WHERE lower(email) = lower(:email) AND deleted_at IS NULL",
                jdbc.params().addValue("email", email));
    }

    public List<User> findAll() {
        return jdbc.findList(SELECT + " WHERE deleted_at IS NULL ORDER BY id", jdbc.params(), rows.user);
    }

    public long count() {
        return jdbc.count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", jdbc.params());
    }

    public User save(User user) {
        if (user.getId() == null) {
            user.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO users (email, password_hash, phone_number, status, email_verified_at, locale, timezone,
                                       last_login_at, first_name, last_name, created_at, updated_at)
                    VALUES (:email, :passwordHash, :phoneNumber, :status, :emailVerifiedAt, :locale, :timezone,
                            :lastLoginAt, :firstName, :lastName, :createdAt, :updatedAt)
                    """, bind(user));
            user.setId(id);
            return user;
        }
        user.onUpdate();
        jdbc.update("""
                UPDATE users SET email = :email, password_hash = :passwordHash, phone_number = :phoneNumber,
                    status = :status, email_verified_at = :emailVerifiedAt, locale = :locale, timezone = :timezone,
                    last_login_at = :lastLoginAt, first_name = :firstName, last_name = :lastName, updated_at = :updatedAt
                WHERE id = :id
                """, bind(user).addValue("id", user.getId()));
        return user;
    }

    public void delete(User user) {
        jdbc.update("UPDATE users SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", user.getId()));
    }

    private MapSqlParameterSource bind(User user) {
        return jdbc.params()
                .addValue("email", user.getEmail())
                .addValue("passwordHash", user.getPasswordHash())
                .addValue("phoneNumber", user.getPhoneNumber())
                .addValue("status", user.getStatus())
                .addValue("emailVerifiedAt", JdbcSupport.ts(user.getEmailVerifiedAt()))
                .addValue("locale", user.getLocale())
                .addValue("timezone", user.getTimezone())
                .addValue("lastLoginAt", JdbcSupport.ts(user.getLastLoginAt()))
                .addValue("firstName", user.getFirstName())
                .addValue("lastName", user.getLastName())
                .addValue("createdAt", JdbcSupport.ts(user.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(user.getUpdatedAt()));
    }
}
