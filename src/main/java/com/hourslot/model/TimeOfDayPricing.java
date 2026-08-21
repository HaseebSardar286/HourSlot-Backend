package com.hourslot.model;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TimeOfDayPricing {

    private Long id;

    private Service service;

    @NotNull
    private int dayOfWeek; // 1 = Monday, 7 = Sunday

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @NotNull
    private double priceMultiplier; // e.g. 1.2 for 20% peak surge

    private String label;

    @Builder.Default
    private boolean active = true;
}
