package com.duemet.booking.repository;

import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class BusinessPolicyRepository {

    /** A business's cancellation / no-show rules. Defaults allow free cancellation at any time. */
    public record BusinessPolicy(Long businessId, int freeCancelHours, BigDecimal lateCancelFeePercent,
                                 BigDecimal noShowFeePercent, int prepayAfterNoShows, String policyText) {
        public static BusinessPolicy defaults(Long businessId) {
            return new BusinessPolicy(businessId, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, null);
        }
    }

    private final JdbcSupport jdbc;

    public BusinessPolicyRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public BusinessPolicy find(Long businessId) {
        return jdbc.findOne("""
                SELECT business_id, free_cancel_hours, late_cancel_fee_percent, no_show_fee_percent,
                       prepay_after_no_shows, policy_text
                FROM business_policies WHERE business_id = :id
                """, jdbc.params().addValue("id", businessId), (rs, i) -> new BusinessPolicy(
                rs.getLong("business_id"),
                rs.getInt("free_cancel_hours"),
                rs.getBigDecimal("late_cancel_fee_percent"),
                rs.getBigDecimal("no_show_fee_percent"),
                rs.getInt("prepay_after_no_shows"),
                rs.getString("policy_text"))).orElse(BusinessPolicy.defaults(businessId));
    }

    public BusinessPolicy save(BusinessPolicy policy) {
        jdbc.update("""
                INSERT INTO business_policies (business_id, free_cancel_hours, late_cancel_fee_percent,
                                               no_show_fee_percent, prepay_after_no_shows, policy_text, updated_at)
                VALUES (:id, :freeCancelHours, :lateFee, :noShowFee, :prepay, :text, NOW())
                ON CONFLICT (business_id) DO UPDATE SET
                    free_cancel_hours = EXCLUDED.free_cancel_hours,
                    late_cancel_fee_percent = EXCLUDED.late_cancel_fee_percent,
                    no_show_fee_percent = EXCLUDED.no_show_fee_percent,
                    prepay_after_no_shows = EXCLUDED.prepay_after_no_shows,
                    policy_text = EXCLUDED.policy_text,
                    updated_at = NOW()
                """, jdbc.params()
                .addValue("id", policy.businessId())
                .addValue("freeCancelHours", policy.freeCancelHours())
                .addValue("lateFee", policy.lateCancelFeePercent())
                .addValue("noShowFee", policy.noShowFeePercent())
                .addValue("prepay", policy.prepayAfterNoShows())
                .addValue("text", policy.policyText()));
        return policy;
    }
}
