package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Category;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BusinessRepository {

    private static final String SELECT = """
            SELECT b.*,
            %s
            FROM businesses b
            """.formatted(RowMappers.BUSINESS_MEDIA_FORMULAS);

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BusinessRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Business> findById(Long id) {
        Optional<Business> found = jdbc.findOne(SELECT + " WHERE b.id = :id AND b.deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.business);
        found.ifPresent(business -> hydrate(business, true));
        return found;
    }

    public List<Business> findAll() {
        List<Business> list = jdbc.findList(SELECT + " WHERE b.deleted_at IS NULL ORDER BY b.id",
                jdbc.params(), rows.business);
        list.forEach(business -> hydrate(business, false));
        return list;
    }

    public long count() {
        return jdbc.count("SELECT COUNT(*) FROM businesses WHERE deleted_at IS NULL", jdbc.params());
    }

    public Business save(Business business) {
        if (business.getId() == null) {
            business.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO businesses (organization_id, name, slug, description, status, is_verified,
                                            rejection_reason, registration_number, primary_category_id, rating_avg,
                                            rating_count, timezone, locale, settings, created_at, updated_at)
                    VALUES (:organizationId, :name, :slug, :description, :status, :verified,
                            :rejectionReason, :registrationNumber, :primaryCategoryId, :ratingAvg,
                            :ratingCount, :timezone, :locale, :settings, :createdAt, :updatedAt)
                    """, bind(business));
            business.setId(id);
        } else {
            business.onUpdate();
            jdbc.update("""
                    UPDATE businesses SET organization_id = :organizationId, name = :name, slug = :slug,
                        description = :description, status = :status, is_verified = :verified,
                        rejection_reason = :rejectionReason, registration_number = :registrationNumber,
                        primary_category_id = :primaryCategoryId, rating_avg = :ratingAvg, rating_count = :ratingCount,
                        timezone = :timezone, locale = :locale, settings = :settings, updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(business).addValue("id", business.getId()));
        }
        syncSecondaryCategories(business);
        return business;
    }

    public Optional<Business> findBySlug(String slug) {
        Optional<Business> found = jdbc.findOne(SELECT + " WHERE b.slug = :slug AND b.deleted_at IS NULL",
                jdbc.params().addValue("slug", slug), rows.business);
        found.ifPresent(business -> hydrate(business, false));
        return found;
    }

    public boolean existsBySlug(String slug) {
        return jdbc.exists("SELECT COUNT(*) FROM businesses WHERE slug = :slug AND deleted_at IS NULL",
                jdbc.params().addValue("slug", slug));
    }

    public List<Business> findByVerified(boolean verified) {
        List<Business> list = jdbc.findList(
                SELECT + " WHERE b.is_verified = :verified AND b.deleted_at IS NULL ORDER BY b.id",
                jdbc.params().addValue("verified", verified), rows.business);
        list.forEach(business -> hydrate(business, false));
        return list;
    }

    public Optional<Business> findFirstByMemberUserId(Long userId) {
        Optional<Business> found = jdbc.findOne(SELECT + """
                 JOIN organization_members om ON om.organization_id = b.organization_id
                 WHERE om.user_id = :userId AND om.status = 'ACTIVE' AND om.deleted_at IS NULL
                   AND b.deleted_at IS NULL
                 LIMIT 1
                """, jdbc.params().addValue("userId", userId), rows.business);
        found.ifPresent(business -> hydrate(business, false));
        return found;
    }

    public boolean existsByMemberUserId(Long userId) {
        return jdbc.exists("""
                SELECT COUNT(*) FROM businesses b
                JOIN organization_members om ON om.organization_id = b.organization_id
                WHERE om.user_id = :userId AND om.status = 'ACTIVE' AND om.deleted_at IS NULL
                  AND b.deleted_at IS NULL
                """, jdbc.params().addValue("userId", userId));
    }

    public long countByOrganizationId(Long orgId) {
        return jdbc.count(
                "SELECT COUNT(*) FROM businesses WHERE organization_id = :orgId AND deleted_at IS NULL",
                jdbc.params().addValue("orgId", orgId));
    }

    private void hydrate(Business business, boolean secondary) {
        if (business.getPrimaryCategory() != null && business.getPrimaryCategory().getId() != null) {
            jdbc.findOne("SELECT id, parent_id, name, slug, icon, image_url, search_tags, is_active, sort_order, created_at FROM categories WHERE id = :id",
                    jdbc.params().addValue("id", business.getPrimaryCategory().getId()), rows.category)
                    .ifPresent(business::setPrimaryCategory);
        }
        if (secondary) {
            List<Category> categories = jdbc.findList("""
                    SELECT c.id, c.parent_id, c.name, c.slug, c.icon, c.image_url, c.search_tags, c.is_active,
                           c.sort_order, c.created_at
                    FROM categories c
                    JOIN business_categories bc ON bc.category_id = c.id
                    WHERE bc.business_id = :id
                    """, jdbc.params().addValue("id", business.getId()), rows.category);
            business.setSecondaryCategories(categories);
        }
    }

    private void syncSecondaryCategories(Business business) {
        if (business.getSecondaryCategories() == null || business.getId() == null) {
            return;
        }
        jdbc.update("DELETE FROM business_categories WHERE business_id = :id",
                jdbc.params().addValue("id", business.getId()));
        for (Category category : business.getSecondaryCategories()) {
            if (category == null || category.getId() == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO business_categories (business_id, category_id)
                    VALUES (:businessId, :categoryId)
                    """, jdbc.params()
                    .addValue("businessId", business.getId())
                    .addValue("categoryId", category.getId()));
        }
    }

    private MapSqlParameterSource bind(Business business) {
        return jdbc.params()
                .addValue("organizationId", business.getOrganization() == null ? null : business.getOrganization().getId())
                .addValue("name", business.getName())
                .addValue("slug", business.getSlug())
                .addValue("description", business.getDescription())
                .addValue("status", business.getStatus() == null ? null : business.getStatus().name())
                .addValue("verified", business.isVerified())
                .addValue("rejectionReason", business.getRejectionReason())
                .addValue("registrationNumber", business.getRegistrationNumber())
                .addValue("primaryCategoryId", business.getPrimaryCategory() == null ? null : business.getPrimaryCategory().getId())
                .addValue("ratingAvg", business.getRatingAvg())
                .addValue("ratingCount", business.getRatingCount())
                .addValue("timezone", business.getTimezone())
                .addValue("locale", business.getLocale())
                .addValue("settings", jdbc.jsonb(business.getSettings()))
                .addValue("createdAt", JdbcSupport.ts(business.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(business.getUpdatedAt()));
    }
}
