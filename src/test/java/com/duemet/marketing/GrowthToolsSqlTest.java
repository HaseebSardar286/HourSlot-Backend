package com.duemet.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.duemet.shared.jdbc.JdbcSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Reports, customer records, promo codes and the walk-in queue against the real migrated schema. */
@Testcontainers(disabledWithoutDocker = true)
class GrowthToolsSqlTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static JdbcSupport support;
    private static TransactionTemplate tx;

    @BeforeAll
    static void migrate() {
        var ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").baselineVersion("1").load();
        flyway.baseline();
        flyway.migrate();
        jdbc = new JdbcTemplate(ds);
        support = new JdbcSupport(new NamedParameterJdbcTemplate(ds), new ObjectMapper());
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("INSERT INTO businesses(id, name, slug) VALUES (1, 'Ali Electric', 'ali-electric'), (2, 'Other', 'other')");
        jdbc.update("INSERT INTO branches(id, business_id, name, address) VALUES (1, 1, 'Main', 'addr')");
        jdbc.update("INSERT INTO services(id, business_id, name, duration_minutes) VALUES (1, 1, 'Wiring', 60), (2, 1, 'Fan fitting', 30)");
        jdbc.update("INSERT INTO users(id, email, password_hash, first_name) VALUES (1, 'sara@example.com', 'x', 'Sara'), (2, 'omar@example.com', 'x', 'Omar'), (3, 'nobody@example.com', 'x', 'No')");
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM queue_entries");
        jdbc.update("DELETE FROM promo_codes");
        jdbc.update("DELETE FROM customer_business_tags");
        jdbc.update("DELETE FROM customer_business_notes");
        jdbc.update("DELETE FROM bookings");
    }

    private long booking(long customer, long service, LocalDateTime at, String status, String price) {
        Long id = jdbc.queryForObject("""
                INSERT INTO bookings(business_id, branch_id, customer_user_id, status, booking_time, end_time, total_price)
                VALUES (1, 1, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, customer, status, at, at.plusHours(1), new BigDecimal(price));
        jdbc.update("""
                INSERT INTO booking_items(booking_id, service_id, start_time, end_time, unit_price, line_total)
                VALUES (?, ?, ?, ?, 0, 0)
                """, id, service, at, at.plusHours(1));
        return id;
    }

    @Test
    void reportCountsMoneyNoShowsAndRepeatCustomers() {
        LocalDateTime today10 = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
        booking(1, 1, today10.minusDays(45), "COMPLETED", "1000"); // Sara visited before the period (in the previous 30 days)
        booking(1, 1, today10.minusDays(2), "COMPLETED", "2000");
        booking(2, 2, today10.minusDays(1), "COMPLETED", "500");
        booking(2, 2, today10.minusDays(3), "NO_SHOW", "500");
        booking(1, 1, today10.minusDays(4), "CANCELLED", "2000");

        Map<String, Object> r = new BusinessInsightsService(support).report(1L, 30);

        assertEquals(0, new BigDecimal("2500").compareTo((BigDecimal) r.get("revenue")));
        assertEquals(0, new BigDecimal("1000").compareTo((BigDecimal) r.get("previousRevenue")));
        assertEquals(2L, r.get("completed"));
        assertEquals(1L, r.get("noShows"));
        assertEquals(new BigDecimal("33.3"), r.get("noShowRate"));
        assertEquals(1L, ((Number) r.get("returningCustomers")).longValue());
        assertEquals(1L, ((Number) r.get("newCustomers")).longValue());
        assertEquals(30, ((List<?>) r.get("daily")).size());
        assertEquals(24, ((List<?>) r.get("byHour")).size());
        assertEquals(7, ((List<?>) r.get("byWeekday")).size());
        var at10 = ((List<Map<String, Object>>) r.get("byHour")).get(10);
        assertEquals(3L, ((Number) at10.get("bookings")).longValue()); // cancelled excluded
        assertEquals("Fan fitting", ((List<Map<String, Object>>) r.get("topServices")).get(0).get("service")); // 2 bookings vs 1
    }

    @Test
    void customerRecordsNotesAndTags() {
        booking(1, 1, LocalDateTime.now().minusDays(5), "COMPLETED", "2000");
        booking(1, 1, LocalDateTime.now().minusDays(1), "NO_SHOW", "2000");
        var crm = new CustomerRecordsService(support);

        var rows = crm.list(1L);
        assertEquals(1, rows.size());
        assertEquals(0, new BigDecimal("2000").compareTo((BigDecimal) rows.get(0).get("total_spent")));
        assertEquals(1L, ((Number) rows.get(0).get("no_show_count")).longValue());

        crm.addNote(1L, 1L, 1L, "Prefers mornings");
        assertEquals(List.of("Has dog", "VIP"), crm.setTags(1L, 1L, List.of("VIP", " Has  dog ", "VIP", "")));
        var detail = crm.detail(1L, 1L);
        assertEquals(1, ((List<?>) detail.get("notes")).size());
        assertEquals(2, ((List<?>) detail.get("bookings")).size());
        assertEquals("Wiring", ((List<Map<String, Object>>) detail.get("bookings")).get(0).get("service_name"));
        assertEquals(List.of("Has dog", "VIP"), crm.list(1L).get(0).get("tags"));

        // Someone who never booked here is not visible to the business.
        assertThrows(SecurityException.class, () -> crm.detail(1L, 3L));
        assertThrows(SecurityException.class, () -> crm.detail(2L, 1L));
    }

    @Test
    void promoCodeLimitsAndRelease() {
        var promos = new PromoCodeService(support);
        var code = promos.create(1L, "eid20", "percent", new BigDecimal("20"), new BigDecimal("1000"),
                null, null, 2, 1);
        assertEquals("EID20", code.code());
        assertThrows(IllegalStateException.class,
                () -> promos.create(1L, "EID20", "FIXED", BigDecimal.TEN, null, null, null, null, null));

        assertEquals(new BigDecimal("400.00"), promos.preview(1L, 1L, " Eid20 ", new BigDecimal("2000")).discount());
        assertThrows(IllegalArgumentException.class, () -> promos.preview(1L, 1L, "EID20", new BigDecimal("500")));
        assertThrows(IllegalArgumentException.class, () -> promos.preview(2L, 1L, "EID20", new BigDecimal("2000")));

        long b1 = booking(1, 1, LocalDateTime.now().plusDays(1), "CONFIRMED", "2000");
        tx.executeWithoutResult(s -> promos.redeem(1L, 1L, "EID20", new BigDecimal("2000"), b1));
        // Same customer, limit 1 per customer
        assertThrows(IllegalArgumentException.class, () -> promos.preview(1L, 1L, "EID20", new BigDecimal("2000")));
        long b2 = booking(2, 1, LocalDateTime.now().plusDays(1), "CONFIRMED", "2000");
        tx.executeWithoutResult(s -> promos.redeem(1L, 2L, "EID20", new BigDecimal("2000"), b2));
        // Max 2 uses reached
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (4, 'x4@example.com', 'x') ON CONFLICT DO NOTHING");
        assertThrows(IllegalArgumentException.class, () -> promos.preview(1L, 4L, "EID20", new BigDecimal("2000")));

        promos.release(b1);
        assertDoesNotThrow(() -> promos.preview(1L, 4L, "EID20", new BigDecimal("2000")));
        assertEquals(1L, promos.list(1L).get(0).timesUsed());

        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?", b2);
        assertEquals(1, promos.releaseForCancelledBookings());

        promos.setActive(1L, code.id(), false);
        assertThrows(IllegalArgumentException.class, () -> promos.preview(1L, 4L, "EID20", new BigDecimal("2000")));
        assertThrows(IllegalArgumentException.class, () -> promos.create(1L, "x", "PERCENT", BigDecimal.TEN, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> promos.create(1L, "BIG", "PERCENT", new BigDecimal("150"), null, null, null, null, null));
    }

    @Test
    void walkInQueueTicketsAndBoard() {
        var queue = new WalkInQueueService(support);
        var first = tx.execute(s -> queue.add(1L, "Sara Khan", "0300", 1L));
        var second = tx.execute(s -> queue.add(1L, "Omar", null, null));
        assertEquals(1, first.get("ticket_number"));
        assertEquals(2, second.get("ticket_number"));
        assertThrows(IllegalArgumentException.class, () -> queue.add(1L, " ", null, null));

        var called = tx.execute(s -> queue.callNext(1L));
        assertEquals(1, called.get("ticket_number"));
        var board = queue.publicBoard(1L, "Ali Electric");
        assertEquals(List.of(Map.of("ticket", 1, "name", "Sara K.")), board.get("serving"));
        assertEquals(1, board.get("waitingCount"));
        assertEquals(WalkInQueueService.DEFAULT_SERVICE_MINUTES, board.get("estimatedWaitMinutes"));

        assertThrows(IllegalStateException.class,
                () -> queue.setStatus(1L, ((Number) second.get("id")).longValue(), "DONE")); // not serving yet
        queue.setStatus(1L, ((Number) first.get("id")).longValue(), "DONE");
        queue.setStatus(1L, ((Number) second.get("id")).longValue(), "LEFT");
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> queue.callNext(1L)));
        assertEquals(2, queue.today(1L).size());
        assertTrue(queue.today(2L).isEmpty());
    }
}
