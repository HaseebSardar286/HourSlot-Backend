package com.hourslot.quote.model;

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
public class Quote {
    private Long id;
    private Long requestId;
    private Long providerId;
    private BigDecimal amount;
    @Builder.Default
    private String currency = "PKR";
    private String description;
    private Integer estimatedDurationMinutes;
    private LocalDateTime validUntil;
    @Builder.Default
    private String status = "SENT";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "SENT";
        if (currency == null) currency = "PKR";
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
