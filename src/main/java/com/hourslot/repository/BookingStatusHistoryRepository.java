package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.model.BookingStatusHistory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class BookingStatusHistoryRepository {

    private final JdbcSupport jdbc;

    public BookingStatusHistoryRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public BookingStatusHistory save(BookingStatusHistory history) {
        if (history.getId() == null) {
            history.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO booking_status_history (booking_id, from_status, to_status, changed_by_user_id, reason, created_at)
                    VALUES (:bookingId, :fromStatus, :toStatus, :changedByUserId, :reason, :createdAt)
                    """, bind(history));
            history.setId(id);
            return history;
        }
        jdbc.update("""
                UPDATE booking_status_history SET
                    booking_id = :bookingId,
                    from_status = :fromStatus,
                    to_status = :toStatus,
                    changed_by_user_id = :changedByUserId,
                    reason = :reason,
                    created_at = :createdAt
                WHERE id = :id
                """, bind(history).addValue("id", history.getId()));
        return history;
    }

    private MapSqlParameterSource bind(BookingStatusHistory history) {
        return jdbc.params()
                .addValue("bookingId", history.getBooking() == null ? null : history.getBooking().getId())
                .addValue("fromStatus", history.getFromStatus())
                .addValue("toStatus", history.getToStatus())
                .addValue("changedByUserId", history.getChangedBy() == null ? null : history.getChangedBy().getId())
                .addValue("reason", history.getReason())
                .addValue("createdAt", JdbcSupport.ts(history.getCreatedAt()));
    }
}
