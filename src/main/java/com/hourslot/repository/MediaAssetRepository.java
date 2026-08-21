package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.MediaAsset;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class MediaAssetRepository {

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public MediaAssetRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public MediaAsset save(MediaAsset asset) {
        if (asset.getId() == null) {
            asset.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO media_assets (owner_type, owner_id, storage_key, url, mime_type, bytes, sort_order,
                                              created_at, deleted_at)
                    VALUES (:ownerType, :ownerId, :storageKey, :url, :mimeType, :bytes, :sortOrder, :createdAt, :deletedAt)
                    """, bind(asset));
            asset.setId(id);
            return asset;
        }
        jdbc.update("""
                UPDATE media_assets SET owner_type = :ownerType, owner_id = :ownerId, storage_key = :storageKey,
                    url = :url, mime_type = :mimeType, bytes = :bytes, sort_order = :sortOrder, deleted_at = :deletedAt
                WHERE id = :id
                """, bind(asset).addValue("id", asset.getId()));
        return asset;
    }

    private MapSqlParameterSource bind(MediaAsset asset) {
        return jdbc.params()
                .addValue("ownerType", asset.getOwnerType())
                .addValue("ownerId", asset.getOwnerId())
                .addValue("storageKey", asset.getStorageKey())
                .addValue("url", asset.getUrl())
                .addValue("mimeType", asset.getMimeType())
                .addValue("bytes", asset.getBytes())
                .addValue("sortOrder", asset.getSortOrder())
                .addValue("createdAt", JdbcSupport.ts(asset.getCreatedAt()))
                .addValue("deletedAt", JdbcSupport.ts(asset.getDeletedAt()));
    }
}
