package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.BusinessVerificationDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BusinessVerificationDocumentRepository {

    private static final String SELECT = """
            SELECT id, business_id, document_type, media_asset_id, original_filename, status, review_notes,
                   reviewed_by_user_id, reviewed_at, created_at, updated_at, deleted_at
            FROM business_verification_documents
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public BusinessVerificationDocumentRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public BusinessVerificationDocument save(BusinessVerificationDocument document) {
        if (document.getId() == null) {
            document.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO business_verification_documents (business_id, document_type, media_asset_id,
                            original_filename, status, review_notes, reviewed_by_user_id, reviewed_at,
                            created_at, updated_at)
                    VALUES (:businessId, :documentType, :mediaAssetId, :originalFilename, :status, :reviewNotes,
                            :reviewedByUserId, :reviewedAt, :createdAt, :updatedAt)
                    """, bind(document));
            document.setId(id);
            return document;
        }
        document.onUpdate();
        jdbc.update("""
                UPDATE business_verification_documents SET business_id = :businessId, document_type = :documentType,
                    media_asset_id = :mediaAssetId, original_filename = :originalFilename, status = :status,
                    review_notes = :reviewNotes, reviewed_by_user_id = :reviewedByUserId, reviewed_at = :reviewedAt,
                    updated_at = :updatedAt, deleted_at = :deletedAt
                WHERE id = :id
                """, bind(document)
                .addValue("id", document.getId())
                .addValue("deletedAt", JdbcSupport.ts(document.getDeletedAt())));
        return document;
    }

    public Optional<BusinessVerificationDocument> findById(Long id) {
        Optional<BusinessVerificationDocument> found = jdbc.findOne(
                SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.verificationDocument);
        found.ifPresent(this::attachAsset);
        return found;
    }

    public List<BusinessVerificationDocument> findByBusinessOrderByCreatedAtDesc(Business business) {
        List<BusinessVerificationDocument> list = jdbc.findList(
                SELECT + " WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY created_at DESC",
                jdbc.params().addValue("businessId", business.getId()), rows.verificationDocument);
        list.forEach(this::attachAsset);
        return list;
    }

    public Optional<BusinessVerificationDocument> findByBusinessAndDocumentType(Business business, String documentType) {
        Optional<BusinessVerificationDocument> found = jdbc.findOne(
                SELECT + " WHERE business_id = :businessId AND document_type = :documentType AND deleted_at IS NULL",
                jdbc.params().addValue("businessId", business.getId()).addValue("documentType", documentType),
                rows.verificationDocument);
        found.ifPresent(this::attachAsset);
        return found;
    }

    public long countByBusinessAndStatus(Business business, String status) {
        return jdbc.count("""
                SELECT COUNT(*) FROM business_verification_documents
                WHERE business_id = :businessId AND status = :status AND deleted_at IS NULL
                """, jdbc.params().addValue("businessId", business.getId()).addValue("status", status));
    }

    private void attachAsset(BusinessVerificationDocument document) {
        if (document.getMediaAsset() == null || document.getMediaAsset().getId() == null) {
            return;
        }
        jdbc.findOne("""
                SELECT id, owner_type, owner_id, storage_key, url, mime_type, bytes, sort_order, created_at, deleted_at
                FROM media_assets WHERE id = :id
                """, jdbc.params().addValue("id", document.getMediaAsset().getId()), rows.mediaAsset)
                .ifPresent(document::setMediaAsset);
    }

    private MapSqlParameterSource bind(BusinessVerificationDocument document) {
        return jdbc.params()
                .addValue("businessId", document.getBusiness() == null ? null : document.getBusiness().getId())
                .addValue("documentType", document.getDocumentType())
                .addValue("mediaAssetId", document.getMediaAsset() == null ? null : document.getMediaAsset().getId())
                .addValue("originalFilename", document.getOriginalFilename())
                .addValue("status", document.getStatus())
                .addValue("reviewNotes", document.getReviewNotes())
                .addValue("reviewedByUserId", document.getReviewedBy() == null ? null : document.getReviewedBy().getId())
                .addValue("reviewedAt", JdbcSupport.ts(document.getReviewedAt()))
                .addValue("createdAt", JdbcSupport.ts(document.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(document.getUpdatedAt()));
    }
}
