package com.hourslot.quote;

import com.hourslot.quote.model.Quote;
import com.hourslot.request.model.ServiceRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class QuoteAcceptGuardTest {

    @Test
    void expiredQuoteCannotBeAccepted() {
        Quote quote = Quote.builder()
                .id(1L)
                .requestId(9L)
                .status("SENT")
                .validUntil(LocalDateTime.now().minusMinutes(1))
                .build();
        assertTrue(QuoteAcceptGuard.isExpired(quote, ServiceRequest.builder().status("QUOTED").build()));
    }

    @Test
    void expiredRequestBlocksAccept() {
        Quote quote = Quote.builder()
                .id(1L)
                .requestId(9L)
                .status("SENT")
                .validUntil(LocalDateTime.now().plusDays(1))
                .build();
        ServiceRequest request = ServiceRequest.builder()
                .status("QUOTED")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        assertTrue(QuoteAcceptGuard.isExpired(quote, request));
    }

    @Test
    void openQuoteOnLiveRequestIsAcceptable() {
        Quote quote = Quote.builder()
                .id(1L)
                .requestId(9L)
                .status("SENT")
                .validUntil(LocalDateTime.now().plusDays(2))
                .build();
        ServiceRequest request = ServiceRequest.builder()
                .status("QUOTED")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        assertFalse(QuoteAcceptGuard.isExpired(quote, request));
        assertTrue(QuoteAcceptGuard.isAcceptableStatus(quote.getStatus()));
    }

    @Test
    void rejectedQuoteIsNotAcceptable() {
        assertFalse(QuoteAcceptGuard.isAcceptableStatus("REJECTED"));
        assertFalse(QuoteAcceptGuard.isAcceptableStatus("EXPIRED"));
        assertFalse(QuoteAcceptGuard.isAcceptableStatus("ACCEPTED"));
    }
}
