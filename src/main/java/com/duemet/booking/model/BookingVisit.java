package com.duemet.booking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Home-visit details for a booking: where the provider goes, the promised arrival window, urgency. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingVisit {
    private Long bookingId;
    private Long customerAddressId;
    /** Snapshot of the address at booking time, so later edits to the address book do not move the job. */
    private String addressText;
    private Double latitude;
    private Double longitude;
    @Builder.Default
    private int arrivalWindowMinutes = 0;
    @Builder.Default
    private boolean urgent = false;
    @Builder.Default
    private BigDecimal urgentFee = BigDecimal.ZERO;
    private LocalDateTime createdAt;
}
