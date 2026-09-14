package com.hourslot.availability.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffWorkingInterval {

    private Long id;

    @JsonIgnore
    private StaffWorkingHour workingHour;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    @Builder.Default
    private int sortOrder = 0;
}
