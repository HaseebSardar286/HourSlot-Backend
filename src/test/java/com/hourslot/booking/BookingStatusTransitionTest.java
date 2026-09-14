package com.hourslot.booking;

import com.hourslot.booking.model.BookingStatus;
import com.hourslot.booking.services.BookingStatusRules;
import com.hourslot.identity.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BookingStatusTransitionTest {

    private BookingStatusRules rules;

    @BeforeEach
    void setUp() {
        rules = new BookingStatusRules();
    }

    @Test
    void customerCanCancelConfirmedBooking() {
        assertTrue(rules.isValidTransition(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, UserRole.CUSTOMER));
    }

    @Test
    void customerCannotCompleteBooking() {
        assertFalse(rules.isValidTransition(BookingStatus.CONFIRMED, BookingStatus.COMPLETED, UserRole.CUSTOMER));
    }

    @Test
    void ownerCanMovePendingToConfirmed() {
        assertTrue(rules.isValidTransition(BookingStatus.PENDING, BookingStatus.CONFIRMED, UserRole.BUSINESS_OWNER));
    }

    @Test
    void ownerCannotSkipFromPendingToCompleted() {
        assertFalse(rules.isValidTransition(BookingStatus.PENDING, BookingStatus.COMPLETED, UserRole.BUSINESS_OWNER));
    }

    @Test
    void completedIsTerminal() {
        assertFalse(rules.isValidTransition(BookingStatus.COMPLETED, BookingStatus.CONFIRMED, UserRole.BUSINESS_OWNER));
    }
}
