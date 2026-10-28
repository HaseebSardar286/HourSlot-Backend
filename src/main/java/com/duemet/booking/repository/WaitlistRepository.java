package com.duemet.booking.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class WaitlistRepository {

    public record Entry(Long id, Long customerUserId, Long businessId, Long branchId, Long serviceId,
                        LocalDate desiredDate, String status, LocalDateTime notifiedAt, LocalDateTime createdAt,
                        String businessName, String serviceName) {
    }

    private static final String SELECT = """
            SELECT w.*, biz.name AS business_name, s.name AS service_name
            FROM waitlist_entries w
            JOIN businesses biz ON biz.id = w.business_id
            JOIN services s ON s.id = w.service_id
            """;

    private final JdbcSupport jdbc;

    public WaitlistRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins the waitlist; returns false if the customer is already waiting for that day. */
    public boolean join(Long customerUserId, Long businessId, Long branchId, Long serviceId, LocalDate date) {
        return jdbc.update("""
                INSERT INTO waitlist_entries (customer_user_id, business_id, branch_id, service_id, desired_date, status)
                VALUES (:customer, :business, :branch, :service, :date, 'ACTIVE')
                ON CONFLICT (customer_user_id, branch_id, service_id, desired_date) WHERE status = 'ACTIVE'
                DO NOTHING
                """, jdbc.params().addValue("customer", customerUserId).addValue("business", businessId)
                .addValue("branch", branchId).addValue("service", serviceId).addValue("date", date)) == 1;
    }

    public List<Entry> findForCustomer(Long customerUserId) {
        return jdbc.findList(SELECT + """
                 WHERE w.customer_user_id = :customer AND w.status IN ('ACTIVE', 'NOTIFIED')
                   AND w.desired_date >= CURRENT_DATE
                 ORDER BY w.desired_date
                """, jdbc.params().addValue("customer", customerUserId), (rs, i) -> map(rs));
    }

    /** Active entries a freed slot on this branch/day could satisfy (any service there, so the customer decides). */
    public List<Entry> findActiveFor(Long branchId, Long serviceId, LocalDate date) {
        return jdbc.findList(SELECT + """
                 WHERE w.branch_id = :branch AND w.service_id = :service AND w.desired_date = :date
                   AND w.status = 'ACTIVE'
                 ORDER BY w.created_at
                """, jdbc.params().addValue("branch", branchId).addValue("service", serviceId)
                .addValue("date", date), (rs, i) -> map(rs));
    }

    public void markNotified(Long id) {
        jdbc.update("UPDATE waitlist_entries SET status = 'NOTIFIED', notified_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", id));
    }

    public int cancel(Long id, Long customerUserId) {
        return jdbc.update("""
                UPDATE waitlist_entries SET status = 'CANCELLED'
                WHERE id = :id AND customer_user_id = :customer AND status IN ('ACTIVE', 'NOTIFIED')
                """, jdbc.params().addValue("id", id).addValue("customer", customerUserId));
    }

    private static Entry map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Entry(rs.getLong("id"), rs.getLong("customer_user_id"), rs.getLong("business_id"),
                rs.getLong("branch_id"), rs.getLong("service_id"), rs.getObject("desired_date", LocalDate.class),
                rs.getString("status"), JdbcSupport.localDateTime(rs, "notified_at"),
                JdbcSupport.localDateTime(rs, "created_at"), rs.getString("business_name"),
                rs.getString("service_name"));
    }
}
