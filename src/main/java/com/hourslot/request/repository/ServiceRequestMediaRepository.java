package com.hourslot.request.repository;

import com.hourslot.request.model.ServiceRequestMedia;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ServiceRequestMediaRepository {
    private final JdbcSupport jdbc;
    private final RowMapper<ServiceRequestMedia> mapper = (rs, i) -> ServiceRequestMedia.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getLong("request_id"))
            .mediaAssetId(JdbcSupport.getLong(rs, "media_asset_id"))
            .storageKey(rs.getString("storage_key"))
            .url(rs.getString("url"))
            .mimeType(rs.getString("mime_type"))
            .sortOrder(rs.getInt("sort_order"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public ServiceRequestMediaRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public ServiceRequestMedia save(ServiceRequestMedia media) {
        if (media.getCreatedAt() == null) {
            media.setCreatedAt(LocalDateTime.now());
        }
        media.setId(jdbc.insert("""
                INSERT INTO service_request_media
                    (request_id, media_asset_id, storage_key, url, mime_type, sort_order, created_at)
                VALUES (:requestId, :mediaAssetId, :storageKey, :url, :mimeType, :sortOrder, :createdAt)
                """, jdbc.params()
                .addValue("requestId", media.getRequestId())
                .addValue("mediaAssetId", media.getMediaAssetId())
                .addValue("storageKey", media.getStorageKey())
                .addValue("url", media.getUrl())
                .addValue("mimeType", media.getMimeType())
                .addValue("sortOrder", media.getSortOrder())
                .addValue("createdAt", JdbcSupport.ts(media.getCreatedAt()))));
        return media;
    }

    public List<ServiceRequestMedia> findByRequest(Long requestId) {
        return jdbc.findList("""
                SELECT id, request_id, media_asset_id, storage_key, url, mime_type, sort_order, created_at
                FROM service_request_media
                WHERE request_id = :requestId
                ORDER BY sort_order, id
                """, jdbc.params().addValue("requestId", requestId), mapper);
    }
}
