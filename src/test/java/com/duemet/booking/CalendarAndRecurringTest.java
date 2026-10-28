package com.duemet.booking;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingItem;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.booking.repository.CalendarFeedRepository;
import com.duemet.booking.services.CalendarFeedService;
import com.duemet.catalog.model.Service;
import com.duemet.identity.model.User;
import com.duemet.organization.model.Branch;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CalendarAndRecurringTest {

    @SuppressWarnings("unchecked")
    private static List<LocalDateTime> occurrences(LocalDateTime first, String frequency, int count) throws Exception {
        Class<?> c = Class.forName("com.duemet.web.controller.CustomerSchedulingController");
        Method m = c.getDeclaredMethod("occurrences", LocalDateTime.class, String.class, int.class);
        m.setAccessible(true);
        return (List<LocalDateTime>) m.invoke(null, first, frequency, count);
    }

    @Test
    void recurringDatesFollowFrequency() throws Exception {
        LocalDateTime first = LocalDateTime.of(2030, 1, 31, 10, 0);
        assertEquals(LocalDateTime.of(2030, 2, 14, 10, 0), occurrences(first, "WEEKLY", 3).get(2));
        assertEquals(LocalDateTime.of(2030, 2, 28, 10, 0), occurrences(first, "BIWEEKLY", 3).get(2));
        // Month-end clamps instead of skipping a month.
        assertEquals(LocalDateTime.of(2030, 2, 28, 10, 0), occurrences(first, "MONTHLY", 2).get(1));
    }

    @Test
    void feedListsActiveBookingsAsEvents() {
        CalendarFeedRepository feeds = mock(CalendarFeedRepository.class);
        BookingRepository bookings = mock(BookingRepository.class);
        BookingVisitRepository visits = mock(BookingVisitRepository.class);
        when(feeds.businessForToken("tok")).thenReturn(Optional.of(9L));
        LocalDateTime start = LocalDateTime.now().plusDays(1).withNano(0);
        Booking active = Booking.builder().id(11L).status(BookingStatus.CONFIRMED).bookingTime(start)
                .endTime(start.plusHours(1)).customerUser(User.builder().firstName("Sara").build())
                .branch(Branch.builder().address("Shop 4, Main Blvd").build())
                .clientNotes("Gate code 12; ring twice")
                .items(new ArrayList<>(List.of(BookingItem.builder().service(Service.builder().name("Fan, ceiling").build()).build())))
                .build();
        Booking cancelled = Booking.builder().id(12L).status(BookingStatus.CANCELLED).bookingTime(start).build();
        when(bookings.findByBusinessAndBookingTimeBetween(eq(9L), any(), any())).thenReturn(List.of(active, cancelled));
        when(visits.findByBookingId(anyLong())).thenReturn(Optional.empty());

        String ics = new CalendarFeedService(feeds, bookings, visits).render("tok").orElseThrow();

        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("UID:booking-11@duemet"));
        assertFalse(ics.contains("booking-12@"));
        assertTrue(ics.contains("SUMMARY:Fan\\, ceiling — Sara"));
        assertTrue(ics.contains("LOCATION:Shop 4\\, Main Blvd"));
        assertTrue(ics.contains("Gate code 12\\; ring twice"));
        assertTrue(ics.lines().allMatch(l -> l.length() <= 75));
        assertTrue(new CalendarFeedService(feeds, bookings, visits).render("wrong").isEmpty());
    }
}
