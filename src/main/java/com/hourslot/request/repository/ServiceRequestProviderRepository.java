package com.hourslot.request.repository;

import com.hourslot.request.model.ServiceRequestProvider;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRequestProviderRepository {
    private static final String SELECT = """
            SELECT id, request_id, provider_id, match_score, match_reason, response_status, responded_at,
                   viewed_at, sent_at, response_minutes, created_at, updated_at
            FROM service_request_providers
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<ServiceRequestProvider> mapper = (rs, i) -> ServiceRequestProvider.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getLong("request_id"))
            .providerId(rs.getLong("provider_id"))
            .matchScore(rs.getBigDecimal("match_score"))
            .matchReason(rs.getString("match_reason"))
            .responseStatus(rs.getString("response_status"))
            .respondedAt(JdbcSupport.localDateTime(rs, "responded_at"))
            .viewedAt(JdbcSupport.localDateTime(rs, "viewed_at"))
            .sentAt(JdbcSupport.localDateTime(rs, "sent_at"))
            .responseMinutes(JdbcSupport.getInt(rs, "response_minutes"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .build();

    public ServiceRequestProviderRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public ServiceRequestProvider save(ServiceRequestProvider row) {
        if (row.getId() == null) {
            row.onCreate();
            row.setId(jdbc.insert("""
                    INSERT INTO service_request_providers
                        (request_id, provider_id, match_score, match_reason, response_status, responded_at,
                         viewed_at, sent_at, response_minutes, created_at, updated_at)
                    VALUES (:requestId, :providerId, :matchScore, :matchReason, :responseStatus, :respondedAt,
                            :viewedAt, :sentAt, :responseMinutes, :createdAt, :updatedAt)
                    """, bind(row)));
        } else {
            row.onUpdate();
            jdbc.update("""
                    UPDATE service_request_providers SET match_score = :matchScore, match_reason = :matchReason,
                        response_status = :responseStatus, responded_at = :respondedAt,
                        viewed_at = :viewedAt, sent_at = :sentAt, response_minutes = :responseMinutes,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(row).addValue("id", row.getId()));
        }
        return row;
    }

    public List<ServiceRequestProvider> findByRequest(Long requestId) {
        return jdbc.findList(SELECT + " WHERE request_id = :requestId ORDER BY match_score DESC, id",
                jdbc.params().addValue("requestId", requestId), mapper);
    }

    public List<ServiceRequestProvider> findByProvider(Long providerId) {
        return jdbc.findList(SELECT + """
                 WHERE provider_id = :providerId
                 ORDER BY created_at DESC
                """, jdbc.params().addValue("providerId", providerId), mapper);
    }

    public Optional<ServiceRequestProvider> findByRequestAndProvider(Long requestId, Long providerId) {
        return jdbc.findOne(SELECT + " WHERE request_id = :requestId AND provider_id = :providerId",
                jdbc.params().addValue("requestId", requestId).addValue("providerId", providerId), mapper);
    }

    private org.springframework.jdbc.core.namedparam.MapSqlParameterSource bind(ServiceRequestProvider row) {
        return jdbc.params()
                .addValue("requestId", row.getRequestId())
                .addValue("providerId", row.getProviderId())
                .addValue("matchScore", row.getMatchScore())
                .addValue("matchReason", row.getMatchReason())
                .addValue("responseStatus", row.getResponseStatus())
                .addValue("respondedAt", JdbcSupport.ts(row.getRespondedAt()))
                .addValue("viewedAt", JdbcSupport.ts(row.getViewedAt()))
                .addValue("sentAt", JdbcSupport.ts(row.getSentAt()))
                .addValue("responseMinutes", row.getResponseMinutes())
                .addValue("createdAt", JdbcSupport.ts(row.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(row.getUpdatedAt()));
    }
}
