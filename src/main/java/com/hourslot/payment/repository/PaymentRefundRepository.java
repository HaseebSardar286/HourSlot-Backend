package com.hourslot.payment.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.payment.model.Payment;
import com.hourslot.payment.model.PaymentRefund;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentRefundRepository {

    private final JdbcSupport jdbc;

    public PaymentRefundRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public PaymentRefund save(PaymentRefund refund) {
        if (refund.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO payment_refunds (payment_id, amount, reason, provider_refund_id, status, created_at)
                    VALUES (:paymentId, :amount, :reason, :providerRefundId, :status, NOW())
                    """, jdbc.params()
                    .addValue("paymentId", refund.getPayment() == null ? null : refund.getPayment().getId())
                    .addValue("amount", refund.getAmount())
                    .addValue("reason", refund.getReason())
                    .addValue("providerRefundId", refund.getProviderRefundId())
                    .addValue("status", refund.getStatus()));
            refund.setId(id);
        }
        return refund;
    }

    public Optional<PaymentRefund> findByProviderRefundId(String providerRefundId) {
        return jdbc.findOne("""
                SELECT id, payment_id, amount, reason, provider_refund_id, status, created_at
                FROM payment_refunds WHERE provider_refund_id = :providerRefundId
                """, jdbc.params().addValue("providerRefundId", providerRefundId),
                (rs, rowNum) -> PaymentRefund.builder()
                        .id(rs.getLong("id"))
                        .payment(Payment.builder().id(rs.getLong("payment_id")).build())
                        .amount(rs.getBigDecimal("amount"))
                        .reason(rs.getString("reason"))
                        .providerRefundId(rs.getString("provider_refund_id"))
                        .status(rs.getString("status"))
                        .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
                        .build());
    }
}
