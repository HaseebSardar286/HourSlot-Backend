package com.hourslot.job.model;

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
public class Job {
    private Long id;
    private Long bookingId;
    private Long serviceRequestId;
    private Long providerId;
    private Long customerId;
    private Long serviceId;
    private String title;
    private String description;
    private BigDecimal estimatedAmount;
    private BigDecimal finalAmount;
    @Builder.Default
    private String currency = "PKR";
    @Builder.Default
    private String status = "REQUESTED";
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (currency == null) currency = "PKR";
        if (status == null) status = "REQUESTED";
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
