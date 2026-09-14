package com.hourslot.booking.model;

import lombok.*;

import java.time.LocalDateTime;
import com.hourslot.identity.model.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusHistory {

    private Long id;

    private Booking booking;

    private String fromStatus;

    private String toStatus;

    private User changedBy;

    private String reason;

    private LocalDateTime createdAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
