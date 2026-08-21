package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import com.hourslot.model.BusinessStatus;
import com.hourslot.model.Category;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class BranchRepository {

    private static final String SELECT = """
            SELECT id, business_id, name, address, latitude, longitude, phone_number, timezone,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM branches
            """;

    private static final String SELECT_WITH_BUSINESS = """
            SELECT b.id, b.business_id, b.name, b.address, b.latitude, b.longitude, b.phone_number, b.timezone,
                   b.is_active, b.sort_order, b.created_at, b.updated_at, b.deleted_at,
                   biz.id AS biz_id, biz.organization_id AS biz_organization_id, biz.name AS biz_name,
                   biz.slug AS biz_slug, biz.description AS biz_description, biz.status AS biz_status,
                   biz.is_verified AS biz_verified, biz.primary_category_id AS biz_primary_category_id,
                   cat.id AS cat_id, cat.name AS cat_name, cat.slug AS cat_slug,
                   (SELECT ma.url FROM business_media bm JOIN media_assets ma ON ma.id = bm.media_asset_id
                     WHERE bm.business_id = biz.id AND bm.role = 'logo' AND ma.deleted_at IS NULL LIMIT 1) AS logo_url,
                   (SELECT string_agg(ma.url, ',' ORDER BY ma.sort_order) FROM business_media bm
                     JOIN media_assets ma ON ma.id = bm.media_asset_id
                     WHERE bm.business_id = biz.id AND bm.role = 'gallery' AND ma.deleted_at IS NULL) AS gallery_urls
            FROM branches b
            JOIN businesses biz ON biz.id = b.business_id
            LEFT JOIN categories cat ON cat.id = biz.primary_category_id
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;
    private final RowMapper<Branch> withBusiness;

    public BranchRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
        this.withBusiness = (rs, i) -> mapWithBusiness(rs);
    }

    public Optional<Branch> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.branch);
    }

    public Branch save(Branch branch) {
        if (branch.getId() == null) {
            branch.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO branches (business_id, name, address, latitude, longitude, phone_number, timezone,
                                          is_active, sort_order, created_at, updated_at)
                    VALUES (:businessId, :name, :address, :latitude, :longitude, :phoneNumber, :timezone,
                            :active, :sortOrder, :createdAt, :updatedAt)
                    """, bind(branch));
            branch.setId(id);
            return branch;
        }
        branch.onUpdate();
        jdbc.update("""
                UPDATE branches SET business_id = :businessId, name = :name, address = :address, latitude = :latitude,
                    longitude = :longitude, phone_number = :phoneNumber, timezone = :timezone, is_active = :active,
                    sort_order = :sortOrder, updated_at = :updatedAt
                WHERE id = :id
                """, bind(branch).addValue("id", branch.getId()));
        return branch;
    }

    public void delete(Branch branch) {
        jdbc.update("UPDATE branches SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", branch.getId()));
    }

    public List<Branch> findByBusiness(Business business) {
        return jdbc.findList(SELECT + " WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY sort_order, id",
                jdbc.params().addValue("businessId", business.getId()), rows.branch);
    }

    public long countByOrganizationId(Long orgId) {
        return jdbc.count("""
                SELECT COUNT(*) FROM branches br
                JOIN businesses biz ON biz.id = br.business_id
                WHERE biz.organization_id = :orgId AND br.deleted_at IS NULL
                """, jdbc.params().addValue("orgId", orgId));
    }

    public List<Branch> findAllWithBusinessByIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT_WITH_BUSINESS + """
                 WHERE b.deleted_at IS NULL AND b.id IN (:ids)
                """, jdbc.params().addValue("ids", ids), withBusiness);
    }

    public List<Branch> findAllWithBusiness() {
        return jdbc.findList(SELECT_WITH_BUSINESS + " WHERE b.deleted_at IS NULL ORDER BY b.id",
                jdbc.params(), withBusiness);
    }

    public List<Long> findNearbyBranchIds(double lat, double lon, double radius) {
        return jdbc.findList("""
                SELECT b.id FROM branches b
                INNER JOIN businesses biz ON biz.id = b.business_id
                WHERE b.latitude IS NOT NULL
                  AND b.longitude IS NOT NULL
                  AND b.is_active = true
                  AND b.deleted_at IS NULL
                  AND biz.deleted_at IS NULL
                  AND biz.status = 'APPROVED'
                  AND biz.is_verified = true
                  AND (
                    6371000 * acos(
                      LEAST(1.0, GREATEST(-1.0,
                        cos(radians(:lat)) * cos(radians(b.latitude))
                          * cos(radians(b.longitude) - radians(:lon))
                        + sin(radians(:lat)) * sin(radians(b.latitude))
                      ))
                    )
                  ) <= :radius
                ORDER BY (
                  6371000 * acos(
                    LEAST(1.0, GREATEST(-1.0,
                      cos(radians(:lat)) * cos(radians(b.latitude))
                        * cos(radians(b.longitude) - radians(:lon))
                      + sin(radians(:lat)) * sin(radians(b.latitude))
                    ))
                  )
                )
                """, jdbc.params().addValue("lat", lat).addValue("lon", lon).addValue("radius", radius),
                (rs, i) -> rs.getLong("id"));
    }

    private Branch mapWithBusiness(ResultSet rs) throws SQLException {
        Branch branch = rows.mapBranch(rs);
        Business business = new Business();
        business.setId(JdbcSupport.getLong(rs, "biz_id"));
        business.setOrganization(RowMappers.refOrg(JdbcSupport.getLong(rs, "biz_organization_id")));
        business.setName(rs.getString("biz_name"));
        business.setSlug(rs.getString("biz_slug"));
        business.setDescription(rs.getString("biz_description"));
        String status = rs.getString("biz_status");
        business.setStatus(status == null ? null : BusinessStatus.valueOf(status));
        business.setVerified(rs.getBoolean("biz_verified"));
        business.setLogoUrl(rs.getString("logo_url"));
        business.setGalleryUrls(rs.getString("gallery_urls"));
        Long catId = JdbcSupport.getLong(rs, "cat_id");
        if (catId != null) {
            Category category = new Category();
            category.setId(catId);
            category.setName(rs.getString("cat_name"));
            category.setSlug(rs.getString("cat_slug"));
            business.setPrimaryCategory(category);
        }
        branch.setBusiness(business);
        return branch;
    }

    private MapSqlParameterSource bind(Branch branch) {
        return jdbc.params()
                .addValue("businessId", branch.getBusiness() == null ? null : branch.getBusiness().getId())
                .addValue("name", branch.getName())
                .addValue("address", branch.getAddress())
                .addValue("latitude", branch.getLatitude())
                .addValue("longitude", branch.getLongitude())
                .addValue("phoneNumber", branch.getPhoneNumber())
                .addValue("timezone", branch.getTimezone())
                .addValue("active", branch.isActive())
                .addValue("sortOrder", branch.getSortOrder())
                .addValue("createdAt", JdbcSupport.ts(branch.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(branch.getUpdatedAt()));
    }
}
