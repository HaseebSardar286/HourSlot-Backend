package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.BusinessMedia;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BusinessMediaRepository {

    private static final String SELECT = """
            SELECT business_id, media_asset_id, role
            FROM business_media
            """;

    private static final String SELECT_WITH_ASSETS = """
            SELECT bm.business_id, bm.media_asset_id, bm.role,
                   ma.id, ma.owner_type, ma.owner_id, ma.storage_key, ma.url, ma.mime_type,
                   ma.bytes, ma.sort_order, ma.created_at, ma.deleted_at
            FROM business_media bm
            JOIN media_assets ma ON ma.id = bm.media_asset_id
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BusinessMediaRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public BusinessMedia save(BusinessMedia media) {
        Long businessId = media.getBusinessId() != null
                ? media.getBusinessId()
                : (media.getBusiness() == null ? null : media.getBusiness().getId());
        Long mediaAssetId = media.getMediaAssetId() != null
                ? media.getMediaAssetId()
                : (media.getMediaAsset() == null ? null : media.getMediaAsset().getId());
        media.setBusinessId(businessId);
        media.setMediaAssetId(mediaAssetId);
        jdbc.update("""
                INSERT INTO business_media (business_id, media_asset_id, role)
                VALUES (:businessId, :mediaAssetId, :role)
                ON CONFLICT (business_id, media_asset_id) DO UPDATE SET role = EXCLUDED.role
                """, jdbc.params()
                .addValue("businessId", businessId)
                .addValue("mediaAssetId", mediaAssetId)
                .addValue("role", media.getRole()));
        return media;
    }

    public void delete(BusinessMedia media) {
        Long businessId = media.getBusinessId() != null
                ? media.getBusinessId()
                : (media.getBusiness() == null ? null : media.getBusiness().getId());
        Long mediaAssetId = media.getMediaAssetId() != null
                ? media.getMediaAssetId()
                : (media.getMediaAsset() == null ? null : media.getMediaAsset().getId());
        jdbc.update("""
                DELETE FROM business_media WHERE business_id = :businessId AND media_asset_id = :mediaAssetId
                """, jdbc.params().addValue("businessId", businessId).addValue("mediaAssetId", mediaAssetId));
    }

    public Optional<BusinessMedia> findFirstByBusinessIdAndRole(Long businessId, String role) {
        return jdbc.findOne(SELECT_WITH_ASSETS + """
                 WHERE bm.business_id = :businessId AND bm.role = :role
                 ORDER BY ma.sort_order ASC
                 LIMIT 1
                """, jdbc.params().addValue("businessId", businessId).addValue("role", role), this::mapWithAsset);
    }

    public List<BusinessMedia> findByBusinessIdAndRole(Long businessId, String role) {
        return jdbc.findList(SELECT + " WHERE business_id = :businessId AND role = :role",
                jdbc.params().addValue("businessId", businessId).addValue("role", role), rows.businessMedia);
    }

    public List<BusinessMedia> findWithAssetsByBusinessId(Long businessId) {
        return jdbc.findList(SELECT_WITH_ASSETS + """
                 WHERE bm.business_id = :businessId
                 ORDER BY ma.sort_order ASC
                """, jdbc.params().addValue("businessId", businessId), this::mapWithAsset);
    }

    private BusinessMedia mapWithAsset(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        BusinessMedia media = rows.businessMedia.mapRow(rs, i);
        media.setMediaAsset(rows.mediaAsset.mapRow(rs, i));
        return media;
    }
}
