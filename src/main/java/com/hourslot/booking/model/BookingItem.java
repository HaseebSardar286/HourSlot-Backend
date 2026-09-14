package com.hourslot.booking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.hourslot.catalog.model.Service;
import com.hourslot.organization.model.Staff;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "booking"})
public class BookingItem {

    private Long id;

    private Booking booking;

    private Service service;

    private Staff staff;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal unitPrice;

    @Builder.Default
    private BigDecimal priceMultiplier = BigDecimal.ONE;

    private BigDecimal lineTotal;

    @Builder.Default
    private Integer sortOrder = 0;
}
