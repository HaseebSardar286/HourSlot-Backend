package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.SystemSetting;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class SystemSettingRepository {

    private static final String SELECT = """
            SELECT key, value, updated_at, updated_by
            FROM system_settings
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public SystemSettingRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<SystemSetting> findById(String key) {
        return jdbc.findOne(SELECT + " WHERE key = :key",
                jdbc.params().addValue("key", key), rows.systemSetting);
    }

    public SystemSetting save(SystemSetting setting) {
        setting.setUpdatedAt(LocalDateTime.now());
        jdbc.update("""
                INSERT INTO system_settings (key, value, updated_at, updated_by)
                VALUES (:key, CAST(:value AS jsonb), :updatedAt, :updatedBy)
                ON CONFLICT (key) DO UPDATE SET
                    value = EXCLUDED.value,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by
                """, bind(setting));
        return setting;
    }

    private MapSqlParameterSource bind(SystemSetting setting) {
        return jdbc.params()
                .addValue("key", setting.getKey())
                .addValue("value", setting.getValue())
                .addValue("updatedAt", JdbcSupport.ts(setting.getUpdatedAt()))
                .addValue("updatedBy", setting.getUpdatedBy() == null ? null : setting.getUpdatedBy().getId());
    }
}
