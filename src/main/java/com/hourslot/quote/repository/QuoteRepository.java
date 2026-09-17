package com.hourslot.quote.repository;

import com.hourslot.quote.model.Quote;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class QuoteRepository {
    private static final String SELECT = """
            SELECT id, request_id, provider_id, amount, currency, description, estimated_duration_minutes,
                   valid_until, status, created_at, updated_at, deleted_at
            FROM quotes
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<Quote> mapper = (rs, i) -> Quote.builder()
            .id(rs.getLong("id"))
            .requestId(rs.getLong("request_id"))
            .providerId(rs.getLong("provider_id"))
            .amount(rs.getBigDecimal("amount"))
            .currency(rs.getString("currency"))
            .description(rs.getString("description"))
            .estimatedDurationMinutes(JdbcSupport.getInt(rs, "estimated_duration_minutes"))
            .validUntil(JdbcSupport.localDateTime(rs, "valid_until"))
            .status(rs.getString("status"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .deletedAt(JdbcSupport.localDateTime(rs, "deleted_at"))
            .build();

    public QuoteRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Quote save(Quote quote) {
        if (quote.getId() == null) {
            quote.onCreate();
            quote.setId(jdbc.insert("""
                    INSERT INTO quotes (request_id, provider_id, amount, currency, description,
                        estimated_duration_minutes, valid_until, status, created_at, updated_at)
                    VALUES (:requestId, :providerId, :amount, :currency, :description,
                        :estimatedDurationMinutes, :validUntil, :status, :createdAt, :updatedAt)
                    """, bind(quote)));
        } else {
            quote.onUpdate();
            jdbc.update("""
                    UPDATE quotes SET amount = :amount, currency = :currency, description = :description,
                        estimated_duration_minutes = :estimatedDurationMinutes, valid_until = :validUntil,
                        status = :status, updated_at = :updatedAt
                    WHERE id = :id AND deleted_at IS NULL
                    """, bind(quote).addValue("id", quote.getId()));
        }
        return quote;
    }

    public Optional<Quote> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), mapper);
    }

    public List<Quote> findByRequest(Long requestId) {
        return jdbc.findList(SELECT + """
                 WHERE request_id = :requestId AND deleted_at IS NULL
                 ORDER BY created_at DESC
                """, jdbc.params().addValue("requestId", requestId), mapper);
    }

    public List<Quote> findByProvider(Long providerId) {
        return jdbc.findList(SELECT + """
                 WHERE provider_id = :providerId AND deleted_at IS NULL
                 ORDER BY created_at DESC
                """, jdbc.params().addValue("providerId", providerId), mapper);
    }

    public int markAcceptedIfOpen(Long quoteId) {
        return jdbc.update("""
                UPDATE quotes SET status = 'ACCEPTED', updated_at = NOW()
                WHERE id = :id AND deleted_at IS NULL AND UPPER(status) IN ('SENT', 'VIEWED')
                """, jdbc.params().addValue("id", quoteId));
    }

    public int expireStale() {
        return jdbc.update("""
                UPDATE quotes SET status = 'EXPIRED', updated_at = NOW()
                WHERE deleted_at IS NULL
                  AND valid_until IS NOT NULL AND valid_until < NOW()
                  AND UPPER(status) IN ('DRAFT', 'SENT', 'VIEWED')
                """, jdbc.params());
    }

    public void rejectOthers(Long requestId, Long acceptedQuoteId) {
        jdbc.update("""
                UPDATE quotes SET status = 'REJECTED', updated_at = NOW()
                WHERE request_id = :requestId AND id <> :acceptedQuoteId
                  AND deleted_at IS NULL AND status IN ('DRAFT','SENT','VIEWED')
                """, jdbc.params().addValue("requestId", requestId).addValue("acceptedQuoteId", acceptedQuoteId));
    }

    private MapSqlParameterSource bind(Quote quote) {
        return jdbc.params()
                .addValue("requestId", quote.getRequestId())
                .addValue("providerId", quote.getProviderId())
                .addValue("amount", quote.getAmount())
                .addValue("currency", quote.getCurrency())
                .addValue("description", quote.getDescription())
                .addValue("estimatedDurationMinutes", quote.getEstimatedDurationMinutes())
                .addValue("validUntil", JdbcSupport.ts(quote.getValidUntil()))
                .addValue("status", quote.getStatus())
                .addValue("createdAt", JdbcSupport.ts(quote.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(quote.getUpdatedAt()));
    }
}
