package com.duemet.booking;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingItem;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingIncidentRepository;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingStatusHistoryRepository;
import com.duemet.booking.repository.BusinessPolicyRepository;
import com.duemet.booking.repository.BusinessPolicyRepository.BusinessPolicy;
import com.duemet.booking.repository.WaitlistRepository;
import com.duemet.booking.services.BookingPolicyService;
import com.duemet.catalog.model.Service;
import com.duemet.catalog.repository.CustomerPackageRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.notification.services.MailService;
import com.duemet.notification.services.NotificationService;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingPolicyServiceTest {

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BusinessPolicyRepository policies = mock(BusinessPolicyRepository.class);
    private final BookingIncidentRepository incidents = mock(BookingIncidentRepository.class);
    private final WaitlistRepository waitlist = mock(WaitlistRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MailService mail = mock(MailService.class);
    private BookingPolicyService service;

    private final User customer = User.builder().id(3L).email("c@example.com").build();
    private final Business business = Business.builder().id(9L).name("Ali Electric").build();
    private final Branch branch = Branch.builder().id(4L).business(business).build();

    @BeforeEach
    void setUp() {
        service = new BookingPolicyService(bookings, policies, incidents, waitlist,
                mock(CustomerPackageRepository.class), mock(BookingStatusHistoryRepository.class),
                users, notifications, mail, "https://duemet.test");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "promoCodeService",
                mock(com.duemet.marketing.PromoCodeService.class));
        // 24h free cancellation, 50% late fee, 100% no-show fee, prepay after 2 no-shows
        when(policies.find(9L)).thenReturn(new BusinessPolicy(9L, 24, new BigDecimal("50"), new BigDecimal("100"), 2, null));
    }

    private Booking booking(LocalDateTime start) {
        return Booking.builder().id(1L).business(business).branch(branch).customerUser(customer)
                .status(BookingStatus.CONFIRMED).bookingTime(start).endTime(start.plusHours(1))
                .totalPrice(new BigDecimal("2000")).currency("PKR")
                .items(new ArrayList<>(List.of(BookingItem.builder().service(Service.builder().id(5L).name("Wiring").build()).build())))
                .build();
    }

    @Test
    void cancellingEarlyIsFree() {
        var result = service.cancelByCustomer(booking(LocalDateTime.now().plusDays(3)), customer, null);
        assertFalse(result.late());
        assertEquals(BigDecimal.ZERO, result.fee());
        verify(incidents).record(eq(1L), eq(9L), eq(3L), eq("CUSTOMER_CANCEL"), any(), eq(BigDecimal.ZERO), any(), eq(3L));
    }

    @Test
    void cancellingInsideWindowOwesLateFee() {
        Booking b = booking(LocalDateTime.now().plusHours(5));
        var result = service.cancelByCustomer(b, customer, null);
        assertTrue(result.late());
        assertEquals(new BigDecimal("1000.00"), result.fee());
        assertEquals(BookingStatus.CANCELLED, b.getStatus());
        verify(incidents).record(eq(1L), eq(9L), eq(3L), eq("CUSTOMER_LATE_CANCEL"), any(),
                eq(new BigDecimal("1000.00")), any(), eq(3L));
    }

    @Test
    void completedBookingCannotBeCancelled() {
        Booking b = booking(LocalDateTime.now().plusDays(1));
        b.setStatus(BookingStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> service.cancelByCustomer(b, customer, null));
    }

    @Test
    void noShowOnlyAfterStartAndChargesNoShowFee() {
        assertThrows(IllegalStateException.class,
                () -> service.markNoShow(booking(LocalDateTime.now().plusHours(1)), customer));
        Booking past = booking(LocalDateTime.now().minusMinutes(30));
        assertEquals(new BigDecimal("2000.00"), service.markNoShow(past, customer));
        assertEquals(BookingStatus.NO_SHOW, past.getStatus());
    }

    @Test
    void repeatNoShowsMustPrepay() {
        when(incidents.countNoShows(3L, 9L)).thenReturn(2);
        assertThrows(IllegalArgumentException.class, () -> service.requirePrepaymentIfNeeded(3L, 9L, "VENUE"));
        assertDoesNotThrow(() -> service.requirePrepaymentIfNeeded(3L, 9L, "ONLINE"));
        when(incidents.countNoShows(3L, 9L)).thenReturn(1);
        assertDoesNotThrow(() -> service.requirePrepaymentIfNeeded(3L, 9L, "VENUE"));
    }

    @Test
    void providerCancellationNotifiesWaitlist() {
        Booking b = booking(LocalDateTime.now().plusDays(2));
        var entry = new WaitlistRepository.Entry(77L, 8L, 9L, 4L, 5L, b.getBookingTime().toLocalDate(),
                "ACTIVE", null, LocalDateTime.now(), "Ali Electric", "Wiring");
        when(waitlist.findActiveFor(4L, 5L, b.getBookingTime().toLocalDate())).thenReturn(List.of(entry));
        when(users.findById(8L)).thenReturn(Optional.of(User.builder().id(8L).email("w@example.com").build()));

        service.cancelByProvider(b, User.builder().id(50L).build(), "Sick");

        verify(notifications).notify(any(), eq("WAITLIST_SLOT_OPEN"), any(), any(), eq(77L));
        verify(mail).sendEmail(eq("w@example.com"), any(), contains("Book it now"));
        verify(waitlist).markNotified(77L);
        verify(incidents).record(eq(1L), eq(9L), eq(3L), eq("PROVIDER_CANCEL"), any(), eq(BigDecimal.ZERO), eq("Sick"), eq(50L));
    }

    @Test
    void reliabilityScoreIgnoresPlainCancellations() {
        var r = new BookingIncidentRepository.Reliability(8, 1, 1, 5);
        assertEquals(80, r.score());
        assertNull(new BookingIncidentRepository.Reliability(0, 0, 0, 3).score());
    }
}
