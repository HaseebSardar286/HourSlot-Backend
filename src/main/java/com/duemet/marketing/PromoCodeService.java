package com.duemet.marketing;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Business promo codes ("EID20" = 20% off). Validated and redeemed inside the booking transaction;
 * a cancelled booking gives its use back.
 */
@Service
public class PromoCodeService {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9_-]{3,32}$");

    public record PromoCode(Long id, Long businessId, String code, String discountType, BigDecimal discountValue,
                            BigDecimal minAmount, LocalDateTime validFrom, LocalDateTime validUntil,
                            Integer maxUses, int perCustomerLimit, boolean active, long timesUsed) {
    }

    public record Applied(Long promoCodeId, String code, BigDecimal discount, BigDecimal finalPrice) {
    }

    private final JdbcSupport jdbc;

    public PromoCodeService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT p.*, (SELECT COUNT(*) FROM promo_redemptions r WHERE r.promo_code_id = p.id) AS times_used
            FROM promo_codes p
            """;

    public List<PromoCode> list(Long businessId) {
        return jdbc.findList(SELECT + " WHERE p.business_id = :b ORDER BY p.created_at DESC",
                jdbc.params().addValue("b", businessId), (rs, i) -> map(rs));
    }

    @Transactional
    public PromoCode create(Long businessId, String rawCode, String discountType, BigDecimal value, BigDecimal minAmount,
                            LocalDateTime validFrom, LocalDateTime validUntil, Integer maxUses, Integer perCustomerLimit) {
        String code = normalize(rawCode);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Code must be 3–32 letters, numbers, '-' or '_'.");
        }
        String type = discountType == null ? "" : discountType.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("PERCENT") && !type.equals("FIXED")) {
            throw new IllegalArgumentException("Discount type must be PERCENT or FIXED.");
        }
        if (value == null || value.signum() <= 0 || (type.equals("PERCENT") && value.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException(type.equals("PERCENT")
                    ? "Percentage must be between 1 and 100." : "Discount amount must be positive.");
        }
        if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new IllegalArgumentException("End date must be after the start date.");
        }
        if (maxUses != null && maxUses < 1) {
            throw new IllegalArgumentException("Maximum uses must be at least 1 (leave empty for unlimited).");
        }
        int perCustomer = perCustomerLimit == null ? 1 : perCustomerLimit;
        if (perCustomer < 1 || perCustomer > 100) {
            throw new IllegalArgumentException("Uses per customer must be between 1 and 100.");
        }
        if (jdbc.exists("SELECT COUNT(*) FROM promo_codes WHERE business_id = :b AND UPPER(code) = :c",
                jdbc.params().addValue("b", businessId).addValue("c", code))) {
            throw new IllegalStateException("You already have a code called " + code + ".");
        }
        Long id = jdbc.insert("""
                INSERT INTO promo_codes (business_id, code, discount_type, discount_value, min_amount,
                                         valid_from, valid_until, max_uses, per_customer_limit)
                VALUES (:b, :code, :type, :value, :min, :from, :until, :max, :per)
                """, jdbc.params().addValue("b", businessId).addValue("code", code).addValue("type", type)
                .addValue("value", value).addValue("min", minAmount == null ? BigDecimal.ZERO : minAmount)
                .addValue("from", JdbcSupport.ts(validFrom)).addValue("until", JdbcSupport.ts(validUntil))
                .addValue("max", maxUses).addValue("per", perCustomer));
        return find(id).orElseThrow();
    }

    public void setActive(Long businessId, Long id, boolean active) {
        if (jdbc.update("UPDATE promo_codes SET active = :a WHERE id = :id AND business_id = :b",
                jdbc.params().addValue("a", active).addValue("id", id).addValue("b", businessId)) == 0) {
            throw new RuntimeException("Promo code not found.");
        }
    }

    /** Work out the discount for a customer without using the code. Throws with a customer-friendly reason. */
    public Applied preview(Long businessId, Long customerId, String rawCode, BigDecimal price) {
        PromoCode promo = findByCode(businessId, normalize(rawCode))
                .orElseThrow(() -> new IllegalArgumentException("That promo code isn't valid for this provider."));
        LocalDateTime now = LocalDateTime.now();
        if (!promo.active()) {
            throw new IllegalArgumentException("That promo code is no longer active.");
        }
        if (promo.validFrom() != null && now.isBefore(promo.validFrom())) {
            throw new IllegalArgumentException("That promo code isn't active yet.");
        }
        if (promo.validUntil() != null && now.isAfter(promo.validUntil())) {
            throw new IllegalArgumentException("That promo code has expired.");
        }
        if (promo.maxUses() != null && promo.timesUsed() >= promo.maxUses()) {
            throw new IllegalArgumentException("That promo code has been fully used.");
        }
        if (customerId != null && jdbc.count("""
                SELECT COUNT(*) FROM promo_redemptions WHERE promo_code_id = :p AND customer_user_id = :c
                """, jdbc.params().addValue("p", promo.id()).addValue("c", customerId)) >= promo.perCustomerLimit()) {
            throw new IllegalArgumentException("You've already used this promo code.");
        }
        BigDecimal base = price == null ? BigDecimal.ZERO : price;
        if (base.compareTo(promo.minAmount()) < 0) {
            throw new IllegalArgumentException("This code needs a minimum spend of " + promo.minAmount() + ".");
        }
        BigDecimal discount = promo.discountType().equals("PERCENT")
                ? base.multiply(promo.discountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promo.discountValue().setScale(2, RoundingMode.HALF_UP);
        discount = discount.min(base);
        return new Applied(promo.id(), promo.code(), discount, base.subtract(discount));
    }

    /**
     * Validate and record use of a code for a booking. Called inside the booking transaction, with a lock
     * on the code so two customers cannot both take its last use.
     */
    @Transactional
    public Applied redeem(Long businessId, Long customerId, String rawCode, BigDecimal price, Long bookingId) {
        jdbc.count("SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(hashtext(:k))) l",
                jdbc.params().addValue("k", "promo:" + businessId + ":" + normalize(rawCode)));
        Applied applied = preview(businessId, customerId, rawCode, price);
        jdbc.update("""
                INSERT INTO promo_redemptions (booking_id, promo_code_id, customer_user_id, discount_amount)
                VALUES (:booking, :promo, :customer, :discount)
                """, jdbc.params().addValue("booking", bookingId).addValue("promo", applied.promoCodeId())
                .addValue("customer", customerId).addValue("discount", applied.discount()));
        return applied;
    }

    /** Give the use back when a booking is cancelled. */
    public void release(Long bookingId) {
        jdbc.update("DELETE FROM promo_redemptions WHERE booking_id = :b", jdbc.params().addValue("b", bookingId));
    }

    /** Give back uses held by bookings that were cancelled in bulk (e.g. expired unpaid checkouts). */
    public int releaseForCancelledBookings() {
        return jdbc.update("""
                DELETE FROM promo_redemptions r USING bookings b
                WHERE r.booking_id = b.id AND UPPER(b.status) = 'CANCELLED'
                """, jdbc.params());
    }

    private Optional<PromoCode> find(Long id) {
        return jdbc.findOne(SELECT + " WHERE p.id = :id", jdbc.params().addValue("id", id), (rs, i) -> map(rs));
    }

    private Optional<PromoCode> findByCode(Long businessId, String code) {
        return jdbc.findOne(SELECT + " WHERE p.business_id = :b AND UPPER(p.code) = :c",
                jdbc.params().addValue("b", businessId).addValue("c", code), (rs, i) -> map(rs));
    }

    static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static PromoCode map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PromoCode(rs.getLong("id"), rs.getLong("business_id"), rs.getString("code"),
                rs.getString("discount_type"), rs.getBigDecimal("discount_value"), rs.getBigDecimal("min_amount"),
                JdbcSupport.localDateTime(rs, "valid_from"), JdbcSupport.localDateTime(rs, "valid_until"),
                JdbcSupport.getInt(rs, "max_uses"), rs.getInt("per_customer_limit"), rs.getBoolean("active"),
                rs.getLong("times_used"));
    }
}
