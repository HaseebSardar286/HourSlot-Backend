package com.hourslot.organization.repository;

import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessServiceArea;
import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BusinessServiceAreaRepository {
    private static final String SELECT = """
            SELECT id, business_id, coverage_type, geo_area_id, area_name, latitude, longitude, radius_km,
                   status, created_at, updated_at, deleted_at
            FROM business_service_areas
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BusinessServiceAreaRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public BusinessServiceArea save(BusinessServiceArea area) {
        if (area.getId() == null) {
            area.onCreate();
            area.setId(jdbc.insert("""
                    INSERT INTO business_service_areas
                        (business_id, coverage_type, geo_area_id, area_name, latitude, longitude, radius_km,
                         status, created_at, updated_at)
                    VALUES (:businessId, :coverageType, :geoAreaId, :areaName, :latitude, :longitude, :radiusKm,
                            :status, :createdAt, :updatedAt)
                    """, bind(area)));
        } else {
            area.onUpdate();
            jdbc.update("""
                    UPDATE business_service_areas SET coverage_type = :coverageType, geo_area_id = :geoAreaId,
                        area_name = :areaName, latitude = :latitude, longitude = :longitude, radius_km = :radiusKm,
                        status = :status, updated_at = :updatedAt
                    WHERE id = :id AND business_id = :businessId AND deleted_at IS NULL
                    """, bind(area).addValue("id", area.getId()));
        }
        return area;
    }

    public Optional<BusinessServiceArea> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.businessServiceArea);
    }

    public List<BusinessServiceArea> findByBusiness(Business business) {
        return findByBusinessId(business.getId());
    }

    public List<BusinessServiceArea> findByBusinessId(Long businessId) {
        return jdbc.findList(SELECT + """
                 WHERE business_id = :businessId AND deleted_at IS NULL
                 ORDER BY id
                """, jdbc.params().addValue("businessId", businessId), rows.businessServiceArea);
    }

    public List<Long> findBusinessIdsByGeoArea(Long geoAreaId) {
        return jdbc.jdbc().query("""
                SELECT DISTINCT business_id
                FROM business_service_areas
                WHERE geo_area_id = :geoAreaId AND deleted_at IS NULL AND status = 'ACTIVE'
                """, jdbc.params().addValue("geoAreaId", geoAreaId), (rs, i) -> rs.getLong("business_id"));
    }

    public List<BusinessServiceArea> findActiveByBusinessIds(List<Long> businessIds) {
        if (businessIds == null || businessIds.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT + """
                 WHERE business_id IN (:ids) AND deleted_at IS NULL AND status = 'ACTIVE'
                 ORDER BY business_id, id
                """, jdbc.params().addValue("ids", businessIds), rows.businessServiceArea);
    }

    public long countByBusiness(Business business) {
        return countByBusinessId(business.getId());
    }

    public long countByBusinessId(Long businessId) {
        return jdbc.count("""
                SELECT COUNT(*) FROM business_service_areas
                WHERE business_id = :businessId AND deleted_at IS NULL AND status = 'ACTIVE'
                """, jdbc.params().addValue("businessId", businessId));
    }

    public long countByOrganizationId(Long organizationId) {
        return jdbc.count("""
                SELECT COUNT(*) FROM business_service_areas bsa
                JOIN businesses b ON b.id = bsa.business_id
                WHERE b.organization_id = :organizationId AND b.deleted_at IS NULL
                  AND bsa.deleted_at IS NULL AND bsa.status = 'ACTIVE'
                """, jdbc.params().addValue("organizationId", organizationId));
    }

    public void delete(BusinessServiceArea area) {
        jdbc.update("""
                UPDATE business_service_areas SET deleted_at = NOW(), updated_at = NOW()
                WHERE id = :id AND business_id = :businessId
                """, jdbc.params().addValue("id", area.getId())
                .addValue("businessId", area.getBusiness().getId()));
    }

    private MapSqlParameterSource bind(BusinessServiceArea area) {
        return jdbc.params()
                .addValue("businessId", area.getBusiness() == null ? null : area.getBusiness().getId())
                .addValue("coverageType", area.getCoverageType())
                .addValue("geoAreaId", area.getGeoArea() == null ? null : area.getGeoArea().getId())
                .addValue("areaName", area.getAreaName())
                .addValue("latitude", area.getLatitude())
                .addValue("longitude", area.getLongitude())
                .addValue("radiusKm", area.getRadiusKm())
                .addValue("status", area.getStatus())
                .addValue("createdAt", JdbcSupport.ts(area.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(area.getUpdatedAt()));
    }
}
