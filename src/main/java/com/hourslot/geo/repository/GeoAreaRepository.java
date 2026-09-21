package com.hourslot.geo.repository;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GeoAreaRepository {
    private static final String SELECT = """
            SELECT id, country_code, region, city, name, slug, latitude, longitude, status,
                   created_at, updated_at, deleted_at
            FROM geo_areas
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public GeoAreaRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public List<GeoArea> findAll() {
        return jdbc.findList(SELECT + " WHERE deleted_at IS NULL ORDER BY country_code, city, name",
                jdbc.params(), rows.geoArea);
    }

    public List<GeoArea> findActive() {
        return jdbc.findList(SELECT + """
                 WHERE status = 'ACTIVE' AND deleted_at IS NULL
                 ORDER BY country_code, city, name
                """, jdbc.params(), rows.geoArea);
    }

    public Optional<GeoArea> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.geoArea);
    }

    public List<GeoArea> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT + " WHERE id IN (:ids) AND deleted_at IS NULL",
                jdbc.params().addValue("ids", ids), rows.geoArea);
    }

    public List<GeoArea> findActiveByCity(String countryCode, String city) {
        // Build filters only when present. PostgreSQL rejects untyped NULL binds in
        // "(:param IS NULL OR ...)" via JDBC ("could not determine data type of parameter").
        StringBuilder sql = new StringBuilder(SELECT);
        sql.append(" WHERE status = 'ACTIVE' AND deleted_at IS NULL");
        var params = jdbc.params();
        if (countryCode != null && !countryCode.isBlank()) {
            sql.append(" AND UPPER(country_code) = UPPER(:countryCode)");
            params.addValue("countryCode", countryCode.trim());
        }
        if (city != null && !city.isBlank()) {
            sql.append(" AND LOWER(city) = LOWER(:city)");
            params.addValue("city", city.trim());
        }
        sql.append(" ORDER BY name");
        return jdbc.findList(sql.toString(), params, rows.geoArea);
    }

    public GeoArea save(GeoArea area) {
        if (area.getCreatedAt() == null) {
            area.setCreatedAt(java.time.LocalDateTime.now());
        }
        area.setUpdatedAt(java.time.LocalDateTime.now());
        if (area.getStatus() == null || area.getStatus().isBlank()) {
            area.setStatus("ACTIVE");
        }
        if (area.getSlug() == null || area.getSlug().isBlank()) {
            area.setSlug(slugify(area.getName()));
        }
        if (area.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO geo_areas (country_code, region, city, name, slug, latitude, longitude, status,
                                           created_at, updated_at)
                    VALUES (:countryCode, :region, :city, :name, :slug, :latitude, :longitude, :status,
                            :createdAt, :updatedAt)
                    """, bind(area));
            area.setId(id);
            return area;
        }
        jdbc.update("""
                UPDATE geo_areas SET country_code = :countryCode, region = :region, city = :city, name = :name,
                    slug = :slug, latitude = :latitude, longitude = :longitude, status = :status,
                    updated_at = :updatedAt
                WHERE id = :id AND deleted_at IS NULL
                """, bind(area).addValue("id", area.getId()));
        return area;
    }

    public void softDelete(Long id) {
        jdbc.update("""
                UPDATE geo_areas SET deleted_at = NOW(), updated_at = NOW(), status = 'INACTIVE'
                WHERE id = :id AND deleted_at IS NULL
                """, jdbc.params().addValue("id", id));
    }

    private org.springframework.jdbc.core.namedparam.MapSqlParameterSource bind(GeoArea area) {
        return jdbc.params()
                .addValue("countryCode", area.getCountryCode())
                .addValue("region", area.getRegion())
                .addValue("city", area.getCity())
                .addValue("name", area.getName())
                .addValue("slug", area.getSlug())
                .addValue("latitude", area.getLatitude())
                .addValue("longitude", area.getLongitude())
                .addValue("status", area.getStatus())
                .addValue("createdAt", JdbcSupport.ts(area.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(area.getUpdatedAt()));
    }

    private static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "area";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
