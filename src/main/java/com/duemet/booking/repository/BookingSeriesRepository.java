package com.duemet.booking.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BookingSeriesRepository {

    public record Series(Long id, Long customerUserId, Long businessId, String frequency, int occurrences) {
    }

    private final JdbcSupport jdbc;

    public BookingSeriesRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Long create(Long customerUserId, Long businessId, String frequency, int occurrences) {
        return jdbc.insert("""
                INSERT INTO booking_series (customer_user_id, business_id, frequency, occurrences)
                VALUES (:customer, :business, :frequency, :occurrences)
                """, jdbc.params().addValue("customer", customerUserId).addValue("business", businessId)
                .addValue("frequency", frequency).addValue("occurrences", occurrences));
    }

    public void addBooking(Long seriesId, Long bookingId) {
        jdbc.update("INSERT INTO booking_series_items (series_id, booking_id) VALUES (:series, :booking)",
                jdbc.params().addValue("series", seriesId).addValue("booking", bookingId));
    }

    public Optional<Series> find(Long id) {
        return jdbc.findOne("SELECT * FROM booking_series WHERE id = :id", jdbc.params().addValue("id", id),
                (rs, i) -> new Series(rs.getLong("id"), rs.getLong("customer_user_id"), rs.getLong("business_id"),
                        rs.getString("frequency"), rs.getInt("occurrences")));
    }

    /** Bookings of the series that have not started yet and are still active. */
    public List<Long> upcomingActiveBookingIds(Long seriesId) {
        return jdbc.jdbc().queryForList("""
                SELECT b.id FROM booking_series_items i JOIN bookings b ON b.id = i.booking_id
                WHERE i.series_id = :series AND b.booking_time > NOW()
                  AND b.status IN ('PENDING', 'CONFIRMED') AND b.deleted_at IS NULL
                ORDER BY b.booking_time
                """, jdbc.params().addValue("series", seriesId), Long.class);
    }

    public Optional<Long> seriesIdForBooking(Long bookingId) {
        List<Long> ids = jdbc.jdbc().queryForList(
                "SELECT series_id FROM booking_series_items WHERE booking_id = :booking",
                jdbc.params().addValue("booking", bookingId), Long.class);
        return ids.stream().findFirst();
    }
}
