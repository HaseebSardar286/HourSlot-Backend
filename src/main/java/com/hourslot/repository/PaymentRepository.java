package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Payment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PaymentRepository {

    private static final String SELECT = """
            SELECT id, organization_id, business_id, user_id, purpose, reference_type, reference_id, provider,
                   provider_payment_id, amount, currency, status, raw_payload, created_at, updated_at, version
            FROM payments
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public PaymentRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            payment.onCreate();
            if (payment.getVersion() == null) {
                payment.setVersion(1);
            }
            Long id = jdbc.insert("""
                    INSERT INTO payments (organization_id, business_id, user_id, purpose, reference_type, reference_id,
                                          provider, provider_payment_id, amount, currency, status, raw_payload,
                                          created_at, updated_at, version)
                    VALUES (:organizationId, :businessId, :userId, :purpose, :referenceType, :referenceId, :provider,
                            :providerPaymentId, :amount, :currency, :status, :rawPayload, :createdAt, :updatedAt, :version)
                    """, bind(payment));
            payment.setId(id);
            return payment;
        }
        payment.onUpdate();
        jdbc.update("""
                UPDATE payments SET organization_id = :organizationId, business_id = :businessId, user_id = :userId,
                    purpose = :purpose, reference_type = :referenceType, reference_id = :referenceId, provider = :provider,
                    provider_payment_id = :providerPaymentId, amount = :amount, currency = :currency, status = :status,
                    raw_payload = :rawPayload, updated_at = :updatedAt, version = version + 1
                WHERE id = :id
                """, bind(payment).addValue("id", payment.getId()));
        payment.setVersion(payment.getVersion() == null ? 1 : payment.getVersion() + 1);
        return payment;
    }

    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        return jdbc.findOne(SELECT + " WHERE provider_payment_id = :providerPaymentId",
                jdbc.params().addValue("providerPaymentId", providerPaymentId), rows.payment);
    }

    private MapSqlParameterSource bind(Payment payment) {
        return jdbc.params()
                .addValue("organizationId", payment.getOrganization() == null ? null : payment.getOrganization().getId())
                .addValue("businessId", payment.getBusiness() == null ? null : payment.getBusiness().getId())
                .addValue("userId", payment.getUser() == null ? null : payment.getUser().getId())
                .addValue("purpose", payment.getPurpose())
                .addValue("referenceType", payment.getReferenceType())
                .addValue("referenceId", payment.getReferenceId())
                .addValue("provider", payment.getProvider())
                .addValue("providerPaymentId", payment.getProviderPaymentId())
                .addValue("amount", payment.getAmount())
                .addValue("currency", payment.getCurrency())
                .addValue("status", payment.getStatus())
                .addValue("rawPayload", jdbc.jsonb(payment.getRawPayload()))
                .addValue("createdAt", JdbcSupport.ts(payment.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(payment.getUpdatedAt()))
                .addValue("version", payment.getVersion());
    }
}
