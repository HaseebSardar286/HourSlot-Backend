package com.duemet.marketing;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business reports for a period: money, bookings, no-shows, repeat customers, busy times and top services.
 * Days and hours are computed in the app time zone so "Tuesday 6pm" matches the provider's clock.
 */
@Service
public class BusinessInsightsService {

    private final JdbcSupport jdbc;

    public BusinessInsightsService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> report(Long businessId, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        MapSqlParameterSource p = jdbc.params()
                .addValue("b", businessId)
                .addValue("from", from)
                .addValue("toExclusive", to.plusDays(1))
                .addValue("prevFrom", from.minusDays(days))
                .addValue("tz", ZoneId.systemDefault().getId());
        // Bookings in [from, to] by local calendar day.
        // Leading space matters: text blocks strip the trailing space before each "+ inPeriod".
        String inPeriod = " " + """
                b.business_id = :b AND b.deleted_at IS NULL
                AND (b.booking_time AT TIME ZONE :tz) >= :from AND (b.booking_time AT TIME ZONE :tz) < :toExclusive
                """;

        Map<String, Object> totals = jdbc.jdbc().queryForMap("""
                SELECT
                  COUNT(*) AS bookings,
                  COUNT(*) FILTER (WHERE b.status = 'COMPLETED') AS completed,
                  COUNT(*) FILTER (WHERE b.status = 'CANCELLED') AS cancelled,
                  COUNT(*) FILTER (WHERE b.status = 'NO_SHOW') AS no_shows,
                  COUNT(*) FILTER (WHERE b.status IN ('PENDING','CONFIRMED','IN_PROGRESS')) AS upcoming,
                  COALESCE(SUM(b.total_price) FILTER (WHERE b.status = 'COMPLETED'), 0) AS revenue,
                  COALESCE(SUM(b.total_price) FILTER (WHERE UPPER(COALESCE(b.payment_status,'')) LIKE 'PAID%'), 0) AS collected,
                  COUNT(DISTINCT b.customer_user_id) AS customers
                FROM bookings b WHERE """ + inPeriod, p);

        BigDecimal previousRevenue = jdbc.jdbc().queryForObject("""
                SELECT COALESCE(SUM(b.total_price), 0) FROM bookings b
                WHERE b.business_id = :b AND b.deleted_at IS NULL AND b.status = 'COMPLETED'
                  AND (b.booking_time AT TIME ZONE :tz) >= :prevFrom AND (b.booking_time AT TIME ZONE :tz) < :from
                """, p, BigDecimal.class);

        // A customer is "returning" if they had a completed booking here before this period.
        Map<String, Object> customers = jdbc.jdbc().queryForMap("""
                SELECT
                  COUNT(*) FILTER (WHERE earlier) AS returning,
                  COUNT(*) FILTER (WHERE NOT earlier) AS new
                FROM (
                  SELECT DISTINCT b.customer_user_id,
                         EXISTS (SELECT 1 FROM bookings e WHERE e.business_id = :b AND e.customer_user_id = b.customer_user_id
                                   AND e.status = 'COMPLETED' AND (e.booking_time AT TIME ZONE :tz) < :from) AS earlier
                  FROM bookings b WHERE b.customer_user_id IS NOT NULL AND b.status <> 'CANCELLED' AND """ + inPeriod + """
                ) c
                """, p);

        List<Map<String, Object>> daily = jdbc.jdbc().queryForList("""
                SELECT d::date AS day,
                       COUNT(b.id) FILTER (WHERE b.status <> 'CANCELLED') AS bookings,
                       COALESCE(SUM(b.total_price) FILTER (WHERE b.status = 'COMPLETED'), 0) AS revenue
                FROM generate_series(CAST(:from AS date), CAST(:toExclusive AS date) - 1, INTERVAL '1 day') d
                LEFT JOIN bookings b ON b.business_id = :b AND b.deleted_at IS NULL
                     AND (b.booking_time AT TIME ZONE :tz)::date = d::date
                GROUP BY d ORDER BY d
                """, p);

        List<Map<String, Object>> byHour = jdbc.jdbc().queryForList("""
                SELECT h AS hour, COUNT(b.id) AS bookings
                FROM generate_series(0, 23) h
                LEFT JOIN bookings b ON EXTRACT(HOUR FROM b.booking_time AT TIME ZONE :tz) = h
                     AND b.status <> 'CANCELLED' AND """ + inPeriod + """
                GROUP BY h ORDER BY h
                """, p);

        // ISO weekday: 1 = Monday ... 7 = Sunday
        List<Map<String, Object>> byWeekday = jdbc.jdbc().queryForList("""
                SELECT w AS weekday, COUNT(b.id) AS bookings
                FROM generate_series(1, 7) w
                LEFT JOIN bookings b ON EXTRACT(ISODOW FROM b.booking_time AT TIME ZONE :tz) = w
                     AND b.status <> 'CANCELLED' AND """ + inPeriod + """
                GROUP BY w ORDER BY w
                """, p);

        List<Map<String, Object>> topServices = jdbc.jdbc().queryForList("""
                SELECT s.name AS service, COUNT(DISTINCT b.id) AS bookings,
                       COALESCE(SUM(b.total_price) FILTER (WHERE b.status = 'COMPLETED'), 0) AS revenue
                FROM bookings b
                JOIN booking_items i ON i.booking_id = b.id
                JOIN services s ON s.id = i.service_id
                WHERE b.status <> 'CANCELLED' AND """ + inPeriod + """
                GROUP BY s.name ORDER BY bookings DESC, revenue DESC LIMIT 5
                """, p);

        long completed = number(totals.get("completed"));
        long noShows = number(totals.get("no_shows"));
        BigDecimal revenue = (BigDecimal) totals.get("revenue");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("days", days);
        result.put("revenue", revenue);
        result.put("previousRevenue", previousRevenue);
        result.put("collected", totals.get("collected"));
        result.put("bookings", totals.get("bookings"));
        result.put("completed", completed);
        result.put("cancelled", totals.get("cancelled"));
        result.put("noShows", noShows);
        result.put("upcoming", totals.get("upcoming"));
        result.put("noShowRate", completed + noShows == 0 ? null
                : BigDecimal.valueOf(100.0 * noShows / (completed + noShows)).setScale(1, RoundingMode.HALF_UP));
        result.put("averageBookingValue", completed == 0 ? null
                : revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP));
        result.put("customers", totals.get("customers"));
        result.put("newCustomers", customers.get("new"));
        result.put("returningCustomers", customers.get("returning"));
        result.put("daily", daily);
        result.put("byHour", byHour);
        result.put("byWeekday", byWeekday);
        result.put("topServices", topServices);
        return result;
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }
}
