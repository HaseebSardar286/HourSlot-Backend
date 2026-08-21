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
public class BranchBreak {

    private Long id;

    private BranchWorkingHour workingHour;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
