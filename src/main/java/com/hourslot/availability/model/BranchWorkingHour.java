package com.hourslot.availability.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import com.hourslot.organization.model.Branch;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BranchWorkingHour {

    private Long id;

    @JsonIgnoreProperties({"business", "hibernateLazyInitializer", "handler"})
    private Branch branch;

    @NotNull
    private int dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private boolean closed;

    @Builder.Default
    private int slotStepMinutes = 30;

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<BranchBreak> breaks = new ArrayList<>();

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<BranchWorkingInterval> intervals = new ArrayList<>();
}

