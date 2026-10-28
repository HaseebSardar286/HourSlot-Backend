package com.duemet.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.shared.jdbc.JdbcSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the real Flyway migrations against Postgres and checks both double-booking defences:
 * the per-staff advisory lock and the ex_booking_items_staff_overlap constraint.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class BookingConcurrencyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static BookingRepository bookingRepository;
    private static TransactionTemplate tx;

    private static final OffsetDateTime DAY = OffsetDateTime.parse("2030-01-01T00:00:00+05:00");

    @BeforeAll
    static void migrate() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        // V1 assumes a pre-existing schema; existing databases are baselined at version 1, so do the same here.
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineVersion("1").load();
        flyway.baseline();
        flyway.migrate();
        jdbc = new JdbcTemplate(dataSource);
        bookingRepository = new BookingRepository(
                new JdbcSupport(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper()), null, null);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        jdbc.update("INSERT INTO businesses(id, name) VALUES (1, 'Biz')");
        jdbc.update("INSERT INTO branches(id, business_id, name, address) VALUES (1, 1, 'Main', 'addr')");
        jdbc.update("INSERT INTO staff(id, branch_id, display_name) VALUES (1, 1, 'Ali')");
        jdbc.update("INSERT INTO services(id, business_id, name, duration_minutes) VALUES (1, 1, 'Wiring', 60)");
        jdbc.update("INSERT INTO users(id, email, password_hash) VALUES (1, 'c@example.com', 'x')");
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM waitlist_entries");
        jdbc.update("DELETE FROM booking_series");
        jdbc.update("DELETE FROM bookings");
    }

    /** Phase 2 repositories against the real schema, to catch SQL that unit tests with mocks cannot. */
    @Test
    void phaseTwoRepositoriesWorkOnRealSchema() {
        JdbcSupport support = new JdbcSupport(new NamedParameterJdbcTemplate(dataSource), new ObjectMapper());
        long booking = insertBooking(DAY.plusHours(9), DAY.plusHours(10));
        jdbc.update("UPDATE bookings SET customer_user_id = 1, business_id = 1, status = 'COMPLETED' WHERE id = ?", booking);
        long missed = insertBooking(DAY.plusHours(12), DAY.plusHours(13));
        jdbc.update("UPDATE bookings SET customer_user_id = 1, business_id = 1 WHERE id = ?", missed);

        var policies = new com.duemet.booking.repository.BusinessPolicyRepository(support);
        assertEquals(0, policies.find(1L).freeCancelHours());
        policies.save(new com.duemet.booking.repository.BusinessPolicyRepository.BusinessPolicy(
                1L, 24, new java.math.BigDecimal("50"), java.math.BigDecimal.TEN, 2, "Be on time"));
        assertEquals(24, policies.find(1L).freeCancelHours());

        var incidents = new com.duemet.booking.repository.BookingIncidentRepository(support);
        incidents.record(missed, 1L, 1L, "NO_SHOW", java.math.BigDecimal.ZERO, new java.math.BigDecimal("200"), null, 1L);
        incidents.record(missed, 1L, 1L, "NO_SHOW", java.math.BigDecimal.ZERO, java.math.BigDecimal.ONE, null, 1L); // ignored duplicate
        assertEquals(1, incidents.countNoShows(1L, 1L));
        assertEquals(1, incidents.findOwedByBusiness(1L).size());
        assertNotNull(incidents.customerReliability(1L));
        assertNotNull(incidents.providerReliability(1L));
        assertEquals(1, incidents.setFeeStatus(missed, 1L, "COLLECTED"));
        assertTrue(incidents.findOwedByBusiness(1L).isEmpty());

        var waitlist = new com.duemet.booking.repository.WaitlistRepository(support);
        java.time.LocalDate day = java.time.LocalDate.now().plusDays(3);
        assertTrue(waitlist.join(1L, 1L, 1L, 1L, day));
        assertFalse(waitlist.join(1L, 1L, 1L, 1L, day));
        var active = waitlist.findActiveFor(1L, 1L, day);
        assertEquals(1, active.size());
        assertEquals("Wiring", active.get(0).serviceName());
        waitlist.markNotified(active.get(0).id());
        assertTrue(waitlist.findActiveFor(1L, 1L, day).isEmpty());
        assertEquals(1, waitlist.findForCustomer(1L).size());
        assertEquals(1, waitlist.cancel(active.get(0).id(), 1L));

        var series = new com.duemet.booking.repository.BookingSeriesRepository(support);
        Long seriesId = series.create(1L, 1L, "WEEKLY", 2);
        series.addBooking(seriesId, booking);
        assertEquals(seriesId, series.seriesIdForBooking(booking).orElseThrow());
        assertEquals("WEEKLY", series.find(seriesId).orElseThrow().frequency());
        assertNotNull(series.upcomingActiveBookingIds(seriesId));

        var feeds = new com.duemet.booking.repository.CalendarFeedRepository(support);
        feeds.setToken(1L, "abc");
        feeds.setToken(1L, "def");
        assertEquals(1L, feeds.businessForToken("def").orElseThrow());
        assertTrue(feeds.businessForToken("abc").isEmpty());
    }

    @Test
    void concurrentOverlappingBookingsForOneStaffYieldExactlyOne() throws Exception {
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            // Different start times (10:00, 10:05, ...) all overlap — the case the old exact-time key missed.
            OffsetDateTime begin = DAY.plusHours(10).plusMinutes(5L * (i % 6));
            results.add(pool.submit(() -> {
                start.await();
                return tx.execute(status -> tryBook(begin, begin.plusMinutes(60)));
            }));
        }
        start.countDown();
        int booked = 0;
        for (Future<Boolean> r : results) {
            if (r.get()) booked++;
        }
        pool.shutdown();

        assertEquals(1, booked);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM booking_items WHERE blocks_slot", Integer.class));
    }

    @Test
    void databaseRejectsOverlapEvenWithoutTheLock() {
        insertBooking(DAY.plusHours(9), DAY.plusHours(12));
        assertThrows(DataIntegrityViolationException.class,
                () -> insertBooking(DAY.plusHours(11), DAY.plusHours(11).plusMinutes(30)));
    }

    @Test
    void cancellingABookingFreesTheSlot() {
        long first = insertBooking(DAY.plusHours(9), DAY.plusHours(10));
        jdbc.update("UPDATE bookings SET status = 'CANCELLED' WHERE id = ?", first);
        assertDoesNotThrow(() -> insertBooking(DAY.plusHours(9), DAY.plusHours(10)));
    }

    /** Mirrors BookingService: lock the staff scope, check for overlaps, then insert. */
    private Boolean tryBook(OffsetDateTime begin, OffsetDateTime end) {
        bookingRepository.lockSlotScope("booking:staff:1");
        Integer overlaps = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_items
                WHERE staff_id = 1 AND blocks_slot AND start_time < ? AND end_time > ?
                """, Integer.class, end, begin);
        if (overlaps != null && overlaps > 0) {
            return false;
        }
        insertBooking(begin, end);
        return true;
    }

    private long insertBooking(OffsetDateTime begin, OffsetDateTime end) {
        Long id = jdbc.queryForObject(
                "INSERT INTO bookings(branch_id, status, booking_time, end_time) VALUES (1, 'CONFIRMED', ?, ?) RETURNING id",
                Long.class, begin, end);
        jdbc.update("""
                INSERT INTO booking_items(booking_id, service_id, staff_id, start_time, end_time, unit_price, line_total)
                VALUES (?, 1, 1, ?, ?, 0, 0)
                """, id, begin, end);
        return id;
    }
}
