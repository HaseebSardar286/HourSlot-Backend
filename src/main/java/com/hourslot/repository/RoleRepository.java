package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Role;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RoleRepository {

    private static final String SELECT = """
            SELECT id, scope, organization_id, business_id, code, name, is_system, created_at
            FROM roles
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public RoleRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Role> findByCodeAndSystemTrue(String code) {
        return jdbc.findOne(SELECT + " WHERE code = :code AND is_system = true",
                jdbc.params().addValue("code", code), rows.role);
    }
}
