package com.duemet.booking.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class BookingIncidentRepository {

    public static final String CUSTOMER_CANCEL = "CUSTOMER_CANCEL";
    public static final String CUSTOMER_LATE_CANCEL = "CUSTOMER_LATE_CANCEL";
    public static final String PROVIDER_CANCEL = "PROVIDER_CANCEL";
    public static final String NO_SHOW = "NO_SHOW";

    public record Incident(Long id, Long bookingId, Long businessId, Long customerUserId, String kind,
                           BigDecimal hoursBefore, BigDecimal feeAmount, String feeStatus, String reason,
                           LocalDateTime createdAt) {
    }

    /** Track record over a period: how often bookings were kept, cancelled late, or missed. */
    public record Reliability(int completed, int noShows, int lateCancels, int cancels) {
        public int total() {
            return completed + noShows + lateCancels + cancels;
        }

        /** Share of bookings kept (completed), 0-100; null when there is no history yet. */
        public Integer score() {
            int counted = completed + noShows + lateCancels;
            return counted == 0 ? null : Math.round(100f * completed / counted);
        }
    }

    private final JdbcSupport jdbc;

    public BookingIncidentRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Long bookingId, Long businessId, Long customerUserId, String kind, BigDecimal hoursBefore,
                       BigDecimal feeAmount, String reason, Long createdBy) {
        boolean owed = feeAmount != null && feeAmount.signum() > 0;
        jdbc.update("""
                INSERT INTO booking_incidents (booking_id, business_id, customer_user_id, kind, hours_before,
                                               fee_amount, fee_status, reason, created_by, created_at)
                VALUES (:bookingId, :businessId, :customerUserId, :kind, :hoursBefore,
                        :fee, :feeStatus, :reason, :createdBy, NOW())
                ON CONFLICT (booking_id) DO NOTHING
                """, jdbc.params()
                .addValue("bookingId", bookingId)
                .addValue("businessId", businessId)
                .addValue("customerUserId", customerUserId)
                .addValue("kind", kind)
                .addValue("hoursBefore", hoursBefore)
                .addValue("fee", owed ? feeAmount : BigDecimal.ZERO)
                .addValue("feeStatus", owed ? "OWED" : "NONE")
                .addValue("reason", reason)
                .addValue("createdBy", createdBy));
    }

    public Optional<Incident> findByBooking(Long bookingId) {
        return jdbc.findOne("SELECT * FROM booking_incidents WHERE booking_id = :id",
                jdbc.params().addValue("id", bookingId), (rs, i) -> map(rs));
    }

    public List<Incident> findOwedByBusiness(Long businessId) {
        return jdbc.findList("""
                SELECT * FROM booking_incidents
                WHERE business_id = :id AND fee_status = 'OWED' ORDER BY created_at DESC
                """, jdbc.params().addValue("id", businessId), (rs, i) -> map(rs));
    }

    public int setFeeStatus(Long bookingId, Long businessId, String status) {
        return jdbc.update("""
                UPDATE booking_incidents SET fee_status = :status
                WHERE booking_id = :bookingId AND business_id = :businessId AND fee_status = 'OWED'
                """, jdbc.params().addValue("status", status).addValue("bookingId", bookingId)
                .addValue("businessId", businessId));
    }

    public int countNoShows(Long customerUserId, Long businessId) {
        return (int) jdbc.count("""
                SELECT COUNT(*) FROM booking_incidents
                WHERE customer_user_id = :customer AND business_id = :business AND kind = 'NO_SHOW'
                """, jdbc.params().addValue("customer", customerUserId).addValue("business", businessId));
    }

    /** Customer's record across all businesses in the last 12 months. */
    public Reliability customerReliability(Long customerUserId) {
        return jdbc.findOne("""
                SELECT
                  (SELECT COUNT(*) FROM bookings b WHERE b.customer_user_id = :id AND b.status = 'COMPLETED'
                     AND b.booking_time > NOW() - INTERVAL '12 months' AND b.deleted_at IS NULL) AS completed,
                  COUNT(*) FILTER (WHERE kind = 'NO_SHOW') AS no_shows,
                  COUNT(*) FILTER (WHERE kind = 'CUSTOMER_LATE_CANCEL') AS late_cancels,
                  COUNT(*) FILTER (WHERE kind = 'CUSTOMER_CANCEL') AS cancels
                FROM booking_incidents
                WHERE customer_user_id = :id AND created_at > NOW() - INTERVAL '12 months'
                """, jdbc.params().addValue("id", customerUserId), (rs, i) -> new Reliability(
                rs.getInt("completed"), rs.getInt("no_shows"), rs.getInt("late_cancels"), rs.getInt("cancels")))
                .orElse(new Reliability(0, 0, 0, 0));
    }

    /** How often a business kept its bookings vs. cancelled them itself, last 12 months. */
    public Reliability providerReliability(Long businessId) {
        return jdbc.findOne("""
                SELECT
                  (SELECT COUNT(*) FROM bookings b WHERE b.business_id = :id AND b.status = 'COMPLETED'
                     AND b.booking_time > NOW() - INTERVAL '12 months' AND b.deleted_at IS NULL) AS completed,
                  COUNT(*) FILTER (WHERE kind = 'PROVIDER_CANCEL') AS provider_cancels
                FROM booking_incidents
                WHERE business_id = :id AND created_at > NOW() - INTERVAL '12 months'
                """, jdbc.params().addValue("id", businessId), (rs, i) -> new Reliability(
                // Provider cancellations count against the provider like a customer's late cancellation.
                rs.getInt("completed"), 0, rs.getInt("provider_cancels"), 0))
                .orElse(new Reliability(0, 0, 0, 0));
    }

    private static Incident map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Incident(rs.getLong("id"), rs.getLong("booking_id"), rs.getLong("business_id"),
                JdbcSupport.getLong(rs, "customer_user_id"), rs.getString("kind"),
                rs.getBigDecimal("hours_before"), rs.getBigDecimal("fee_amount"), rs.getString("fee_status"),
                rs.getString("reason"), JdbcSupport.localDateTime(rs, "created_at"));
    }
}
