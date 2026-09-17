package com.hourslot.request.model;

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
public class ServiceRequestProvider {
    private Long id;
    private Long requestId;
    private Long providerId;
    @Builder.Default
    private BigDecimal matchScore = BigDecimal.ZERO;
    private String matchReason;
    @Builder.Default
    private String responseStatus = "INVITED";
    private LocalDateTime respondedAt;
    private LocalDateTime viewedAt;
    private LocalDateTime sentAt;
    private Integer responseMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (responseStatus == null) responseStatus = "INVITED";
        if (matchScore == null) matchScore = BigDecimal.ZERO;
        if (sentAt == null) sentAt = createdAt;
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
