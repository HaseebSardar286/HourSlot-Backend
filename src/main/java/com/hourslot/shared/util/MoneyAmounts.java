package com.hourslot.shared.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Stripe unit-amount conversion for ISO 4217 currencies (including zero- and three-decimal).
 */
public final class MoneyAmounts {

    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "UGX", "VND", "VUV",
            "XAF", "XOF", "XPF");

    private static final Set<String> THREE_DECIMAL = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private MoneyAmounts() {}

    public static String iso(String currency) {
        if (currency == null || currency.isBlank()) {
            return "usd";
        }
        return currency.trim().toLowerCase(Locale.ROOT);
    }

    public static long toStripeUnitAmount(double amount, String currency) {
        return toStripeUnitAmount(BigDecimal.valueOf(amount), currency);
    }

    public static long toStripeUnitAmount(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String code = currency == null ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(code)) {
            return value.setScale(0, RoundingMode.HALF_UP).longValue();
        }
        if (THREE_DECIMAL.contains(code)) {
            return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValue();
        }
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }
}
