package com.hourslot.community.repository;

import com.hourslot.community.model.Community;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CommunityRepository {
    private static final String SELECT = """
            SELECT id, name, slug, description, country_code, region, city, area_name, status, created_at, updated_at
            FROM communities
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<Community> mapper = (rs, i) -> Community.builder()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .slug(rs.getString("slug"))
            .description(rs.getString("description"))
            .countryCode(rs.getString("country_code"))
            .region(rs.getString("region"))
            .city(rs.getString("city"))
            .areaName(rs.getString("area_name"))
            .status(rs.getString("status"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .build();

    public CommunityRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public List<Community> findActive() {
        return jdbc.findList(SELECT + " WHERE status = 'ACTIVE' ORDER BY name",
                jdbc.params(), mapper);
    }

    public Optional<Community> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), mapper);
    }
}
