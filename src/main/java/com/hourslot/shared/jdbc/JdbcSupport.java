package com.hourslot.shared.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thin wrapper around NamedParameterJdbcTemplate so every repository writes
 * explicit SQL instead of letting an ORM generate it.
 */
@Component
public class JdbcSupport {

    private static final Logger log = LogManager.getLogger(JdbcSupport.class);

    /** Per-request auth / badge polling — still run, just not printed every time. */
    private static final Set<String> SKIP_LOG_CALLERS = Set.of(
            "UserRepository.findByEmail",
            "MemberRoleRepository.findActiveByUserId",
            "NotificationRepository.countUnreadByUserId",
            "NotificationRepository.countByUserAndReadFalse"
    );

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcSupport(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public NamedParameterJdbcTemplate jdbc() {
        return jdbc;
    }

    private void logQuery(String sql, MapSqlParameterSource params) {
        if (!log.isInfoEnabled()) {
            return;
        }
        StackWalker.StackFrame frame = StackWalker.getInstance()
                .walk(stream -> stream
                        .filter(f -> !f.getClassName().equals(JdbcSupport.class.getName()))
                        .findFirst()
                        .orElse(null));
        String simpleName = "Unknown";
        String method = "";
        int line = 0;
        if (frame != null) {
            String fullClassName = frame.getClassName();
            simpleName = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
            method = frame.getMethodName();
            line = frame.getLineNumber();
        }
        String callerKey = simpleName + "." + method;
        if (SKIP_LOG_CALLERS.contains(callerKey)) {
            return;
        }
        log.info("[SQL Triggered by: {}]\nSQL: {}\nParameters: {}",
                simpleName + "." + method + ":" + line,
                sql,
                params != null ? params.getValues() : "none");
    }

    public <T> Optional<T> findOne(String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        logQuery(sql, params);
        List<T> rows = jdbc.query(sql, params, mapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public <T> List<T> findList(String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        logQuery(sql, params);
        return jdbc.query(sql, params, mapper);
    }

    public int update(String sql, MapSqlParameterSource params) {
        logQuery(sql, params);
        return jdbc.update(sql, params);
    }

    public long count(String sql, MapSqlParameterSource params) {
        logQuery(sql, params);
        Long value = jdbc.queryForObject(sql, params, Long.class);
        return value == null ? 0L : value;
    }

    public boolean exists(String sql, MapSqlParameterSource params) {
        return count(sql, params) > 0;
    }

    public Long insert(String sql, MapSqlParameterSource params) {
        logQuery(sql, params);
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(sql, params, keys, new String[] {"id"});
        Number key = keys.getKey();
        return key == null ? null : key.longValue();
    }

    public MapSqlParameterSource params() {
        return new MapSqlParameterSource();
    }

    public static Timestamp ts(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    public static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    public static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    public static LocalTime localTime(ResultSet rs, String column) throws SQLException {
        java.sql.Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    public static Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    public static Integer getInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static Double getDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    public static BigDecimal getDecimal(ResultSet rs, String column) throws SQLException {
        return rs.getBigDecimal(column);
    }

    public static String optionalString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    public Object jsonb(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(objectMapper.writeValueAsString(value));
            return json;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write jsonb", ex);
        }
    }

    public Map<String, Object> readJsonb(ResultSet rs, String column) throws SQLException {
        String raw = rs.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ex) {
            throw new SQLException("Failed to read jsonb column " + column, ex);
        }
    }

    public static List<String> statusNames(Collection<?> statuses) {
        return statuses.stream().map(Object::toString).toList();
    }

    public static Map<String, Object> emptyMap() {
        return new LinkedHashMap<>();
    }
}
