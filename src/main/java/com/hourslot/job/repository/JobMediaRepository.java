package com.hourslot.job.repository;

import com.hourslot.job.model.JobMedia;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JobMediaRepository {
    private static final String SELECT = """
            SELECT id, job_id, media_asset_id, url, storage_key, mime_type, sort_order, created_at
            FROM job_media
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<JobMedia> mapper = (rs, i) -> JobMedia.builder()
            .id(rs.getLong("id"))
            .jobId(rs.getLong("job_id"))
            .mediaAssetId(JdbcSupport.getLong(rs, "media_asset_id"))
            .url(rs.getString("url"))
            .storageKey(rs.getString("storage_key"))
            .mimeType(rs.getString("mime_type"))
            .sortOrder(rs.getInt("sort_order"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public JobMediaRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public JobMedia save(JobMedia media) {
        media.setId(jdbc.insert("""
                INSERT INTO job_media (job_id, media_asset_id, url, storage_key, mime_type, sort_order, created_at)
                VALUES (:jobId, :mediaAssetId, :url, :storageKey, :mimeType, :sortOrder, NOW())
                """, jdbc.params()
                .addValue("jobId", media.getJobId())
                .addValue("mediaAssetId", media.getMediaAssetId())
                .addValue("url", media.getUrl())
                .addValue("storageKey", media.getStorageKey())
                .addValue("mimeType", media.getMimeType())
                .addValue("sortOrder", media.getSortOrder() == null ? 0 : media.getSortOrder())));
        return media;
    }

    public List<JobMedia> findByJob(Long jobId) {
        return jdbc.findList(SELECT + " WHERE job_id = :jobId ORDER BY sort_order, id",
                jdbc.params().addValue("jobId", jobId), mapper);
    }
}
