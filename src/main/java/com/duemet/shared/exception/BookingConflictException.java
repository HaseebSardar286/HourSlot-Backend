package com.duemet.shared.exception;

/** The requested slot is no longer available (taken, overlapping, or out of capacity). Maps to HTTP 409. */
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}
