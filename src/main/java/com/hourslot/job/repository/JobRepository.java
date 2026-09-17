package com.hourslot.job.repository;

import com.hourslot.job.model.Job;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JobRepository {
    private static final String SELECT = """
            SELECT id, booking_id, service_request_id, provider_id, customer_id, service_id,
                   title, description, estimated_amount, final_amount, currency, status,
                   scheduled_start, scheduled_end, started_at, completed_at, cancelled_at,
                   created_at, updated_at
            FROM jobs
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<Job> mapper = (rs, i) -> Job.builder()
            .id(rs.getLong("id"))
            .bookingId(JdbcSupport.getLong(rs, "booking_id"))
            .serviceRequestId(JdbcSupport.getLong(rs, "service_request_id"))
            .providerId(rs.getLong("provider_id"))
            .customerId(rs.getLong("customer_id"))
            .serviceId(JdbcSupport.getLong(rs, "service_id"))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .estimatedAmount(rs.getBigDecimal("estimated_amount"))
            .finalAmount(rs.getBigDecimal("final_amount"))
            .currency(rs.getString("currency"))
            .status(rs.getString("status"))
            .scheduledStart(JdbcSupport.localDateTime(rs, "scheduled_start"))
            .scheduledEnd(JdbcSupport.localDateTime(rs, "scheduled_end"))
            .startedAt(JdbcSupport.localDateTime(rs, "started_at"))
            .completedAt(JdbcSupport.localDateTime(rs, "completed_at"))
            .cancelledAt(JdbcSupport.localDateTime(rs, "cancelled_at"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .build();

    public JobRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Job save(Job job) {
        if (job.getId() == null) {
            job.onCreate();
            job.setId(jdbc.insert("""
                    INSERT INTO jobs (
                        booking_id, service_request_id, provider_id, customer_id, service_id,
                        title, description, estimated_amount, final_amount, currency, status,
                        scheduled_start, scheduled_end, started_at, completed_at, cancelled_at,
                        created_at, updated_at)
                    VALUES (
                        :bookingId, :serviceRequestId, :providerId, :customerId, :serviceId,
                        :title, :description, :estimatedAmount, :finalAmount, :currency, :status,
                        :scheduledStart, :scheduledEnd, :startedAt, :completedAt, :cancelledAt,
                        :createdAt, :updatedAt)
                    """, bind(job)));
        } else {
            job.onUpdate();
            jdbc.update("""
                    UPDATE jobs SET
                        booking_id = :bookingId, service_request_id = :serviceRequestId,
                        provider_id = :providerId, customer_id = :customerId, service_id = :serviceId,
                        title = :title, description = :description,
                        estimated_amount = :estimatedAmount, final_amount = :finalAmount,
                        currency = :currency, status = :status,
                        scheduled_start = :scheduledStart, scheduled_end = :scheduledEnd,
                        started_at = :startedAt, completed_at = :completedAt, cancelled_at = :cancelledAt,
                        updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(job).addValue("id", job.getId()));
        }
        return job;
    }

    public Optional<Job> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), mapper);
    }

    public Optional<Job> findByBookingId(Long bookingId) {
        return jdbc.findOne(SELECT + " WHERE booking_id = :bookingId",
                jdbc.params().addValue("bookingId", bookingId), mapper);
    }

    public List<Job> findByProvider(Long providerId) {
        return jdbc.findList(SELECT + """
                 WHERE provider_id = :providerId
                 ORDER BY scheduled_start DESC NULLS LAST, created_at DESC
                """, jdbc.params().addValue("providerId", providerId), mapper);
    }

    public List<Job> findByCustomer(Long customerId) {
        return jdbc.findList(SELECT + """
                 WHERE customer_id = :customerId
                 ORDER BY scheduled_start DESC NULLS LAST, created_at DESC
                """, jdbc.params().addValue("customerId", customerId), mapper);
    }

    public List<Job> findByProviderAndScheduledBetween(Long providerId, LocalDateTime start, LocalDateTime end) {
        return jdbc.findList(SELECT + """
                 WHERE provider_id = :providerId
                   AND scheduled_start >= :start AND scheduled_start < :end
                 ORDER BY scheduled_start
                """, jdbc.params()
                .addValue("providerId", providerId)
                .addValue("start", JdbcSupport.ts(start))
                .addValue("end", JdbcSupport.ts(end)), mapper);
    }

    private MapSqlParameterSource bind(Job job) {
        return jdbc.params()
                .addValue("bookingId", job.getBookingId())
                .addValue("serviceRequestId", job.getServiceRequestId())
                .addValue("providerId", job.getProviderId())
                .addValue("customerId", job.getCustomerId())
                .addValue("serviceId", job.getServiceId())
                .addValue("title", job.getTitle())
                .addValue("description", job.getDescription())
                .addValue("estimatedAmount", job.getEstimatedAmount())
                .addValue("finalAmount", job.getFinalAmount())
                .addValue("currency", job.getCurrency())
                .addValue("status", job.getStatus())
                .addValue("scheduledStart", JdbcSupport.ts(job.getScheduledStart()))
                .addValue("scheduledEnd", JdbcSupport.ts(job.getScheduledEnd()))
                .addValue("startedAt", JdbcSupport.ts(job.getStartedAt()))
                .addValue("completedAt", JdbcSupport.ts(job.getCompletedAt()))
                .addValue("cancelledAt", JdbcSupport.ts(job.getCancelledAt()))
                .addValue("createdAt", JdbcSupport.ts(job.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(job.getUpdatedAt()));
    }
}
