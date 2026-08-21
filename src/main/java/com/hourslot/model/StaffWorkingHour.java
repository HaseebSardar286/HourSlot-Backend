package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

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

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<StaffBreak> breaks = new ArrayList<>();
}
