package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffBreak {

    private Long id;

    private StaffWorkingHour workingHour;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
