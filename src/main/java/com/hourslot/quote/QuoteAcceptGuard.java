package com.hourslot.quote;

import com.hourslot.quote.model.Quote;
import com.hourslot.request.model.ServiceRequest;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

public final class QuoteAcceptGuard {
    private static final Set<String> ACCEPTABLE = Set.of("SENT", "VIEWED");

    private QuoteAcceptGuard() {}

    public static boolean isAcceptableStatus(String status) {
        if (status == null) {
            return false;
        }
        return ACCEPTABLE.contains(status.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isExpired(Quote quote, ServiceRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (quote != null && quote.getValidUntil() != null && quote.getValidUntil().isBefore(now)) {
            return true;
        }
        if (request != null && request.getExpiresAt() != null && request.getExpiresAt().isBefore(now)) {
            return true;
        }
        if (request != null && request.getStatus() != null) {
            String status = request.getStatus().toUpperCase(Locale.ROOT);
            if ("EXPIRED".equals(status) || "CANCELLED".equals(status) || "COMPLETED".equals(status)) {
                return true;
            }
        }
        return false;
    }
}
