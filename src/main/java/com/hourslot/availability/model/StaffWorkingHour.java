package com.hourslot.availability.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import com.hourslot.organization.model.Staff;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffWorkingHour {

    private Long id;

    @JsonIgnoreProperties({"branch", "user", "hibernateLazyInitializer", "handler"})
    private Staff staff;

    @NotNull
    private int dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private boolean closed;

    @Builder.Default
    private int slotStepMinutes = 30;

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<StaffBreak> breaks = new ArrayList<>();

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<StaffWorkingInterval> intervals = new ArrayList<>();
}
