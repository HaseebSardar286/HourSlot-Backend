package com.hourslot.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefund {

    private Long id;

    private Payment payment;

    private BigDecimal amount;

    private String reason;

    private String providerRefundId;

    private String status;

    private LocalDateTime createdAt;
}
