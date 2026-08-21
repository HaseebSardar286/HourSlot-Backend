package com.hourslot.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private Long id;

    private Organization organization;

    private Business business;

    private User user;

    private String purpose;

    private String referenceType;

    private Long referenceId;

    @Builder.Default
    private String provider = "OTHER";

    private String providerPaymentId;

    private BigDecimal amount;

    @Builder.Default
    private String currency = "USD";

    private String status;

    private Map<String, Object> rawPayload;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
