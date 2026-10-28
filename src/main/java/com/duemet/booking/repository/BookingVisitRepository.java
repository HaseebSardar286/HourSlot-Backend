package com.duemet.booking.repository;

import com.duemet.booking.model.BookingVisit;
import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BookingVisitRepository {
    private static final String SELECT = """
            SELECT booking_id, customer_address_id, address_text, latitude, longitude,
                   arrival_window_minutes, urgent, urgent_fee, created_at
            FROM booking_visits
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<BookingVisit> mapper = (rs, i) -> BookingVisit.builder()
            .bookingId(rs.getLong("booking_id"))
            .customerAddressId(JdbcSupport.getLong(rs, "customer_address_id"))
            .addressText(rs.getString("address_text"))
            .latitude(JdbcSupport.getDouble(rs, "latitude"))
            .longitude(JdbcSupport.getDouble(rs, "longitude"))
            .arrivalWindowMinutes(rs.getInt("arrival_window_minutes"))
            .urgent(rs.getBoolean("urgent"))
            .urgentFee(rs.getBigDecimal("urgent_fee"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .build();

    public BookingVisitRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public BookingVisit save(BookingVisit visit) {
        jdbc.update("""
                INSERT INTO booking_visits (booking_id, customer_address_id, address_text, latitude, longitude,
                                            arrival_window_minutes, urgent, urgent_fee, created_at)
                VALUES (:bookingId, :customerAddressId, :addressText, :latitude, :longitude,
                        :arrivalWindowMinutes, :urgent, :urgentFee, NOW())
                ON CONFLICT (booking_id) DO UPDATE SET
                    customer_address_id = EXCLUDED.customer_address_id,
                    address_text = EXCLUDED.address_text,
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    arrival_window_minutes = EXCLUDED.arrival_window_minutes,
                    urgent = EXCLUDED.urgent,
                    urgent_fee = EXCLUDED.urgent_fee
                """, jdbc.params()
                .addValue("bookingId", visit.getBookingId())
                .addValue("customerAddressId", visit.getCustomerAddressId())
                .addValue("addressText", visit.getAddressText())
                .addValue("latitude", visit.getLatitude())
                .addValue("longitude", visit.getLongitude())
                .addValue("arrivalWindowMinutes", visit.getArrivalWindowMinutes())
                .addValue("urgent", visit.isUrgent())
                .addValue("urgentFee", visit.getUrgentFee()));
        return visit;
    }

    public Optional<BookingVisit> findByBookingId(Long bookingId) {
        return jdbc.findOne(SELECT + " WHERE booking_id = :bookingId",
                jdbc.params().addValue("bookingId", bookingId), mapper);
    }
}
