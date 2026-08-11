package com.hourslot.service;

import com.hourslot.model.BookingStatus;
import com.hourslot.model.UserRole;
import org.springframework.stereotype.Component;

@Component
public class BookingStatusRules {

    public boolean isValidTransition(BookingStatus current, BookingStatus next, UserRole role) {
        if (current == next) {
            return true;
        }
        if (role == UserRole.CUSTOMER) {
            return next == BookingStatus.CANCELLED
                    && (current == BookingStatus.PENDING || current == BookingStatus.CONFIRMED);
        }
        return switch (current) {
            case PENDING -> next == BookingStatus.CONFIRMED
                    || next == BookingStatus.CANCELLED;
            case CONFIRMED -> next == BookingStatus.IN_PROGRESS
                    || next == BookingStatus.COMPLETED
                    || next == BookingStatus.CANCELLED
                    || next == BookingStatus.NO_SHOW
                    || next == BookingStatus.RESCHEDULED;
            case IN_PROGRESS -> next == BookingStatus.COMPLETED
                    || next == BookingStatus.CANCELLED
                    || next == BookingStatus.NO_SHOW;
            case RESCHEDULED -> next == BookingStatus.CONFIRMED
                    || next == BookingStatus.CANCELLED;
            default -> false;
        };
    }
}
