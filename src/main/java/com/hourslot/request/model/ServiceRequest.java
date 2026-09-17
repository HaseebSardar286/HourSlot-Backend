package com.hourslot.request.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRequest {
    private Long id;
    private Long customerUserId;
    private Long categoryId;
    private Long serviceId;
    private String title;
    private String description;
    private Long customerAddressId;
    private String countryCode;
    private String region;
    private String city;
    private String areaName;
    private Long geoAreaId;
    private Double latitude;
    private Double longitude;
    private LocalDate preferredDate;
    private LocalTime preferredTimeFrom;
    private LocalTime preferredTimeTo;
    @Builder.Default
    private String urgency = "NORMAL";
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    @Builder.Default
    private String currency = "PKR";
    @Builder.Default
    private String status = "OPEN";
    private Long selectedProviderId;
    private Long selectedQuoteId;
    private Long bookingId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;

    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (urgency == null) urgency = "NORMAL";
        if (status == null) status = "OPEN";
        if (currency == null) currency = "PKR";
        if (expiresAt == null) {
            int hours = switch (urgency.toUpperCase()) {
                case "URGENT" -> 24;
                case "SOON" -> 48;
                default -> 72;
            };
            expiresAt = createdAt.plusHours(hours);
        }
    }

    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
