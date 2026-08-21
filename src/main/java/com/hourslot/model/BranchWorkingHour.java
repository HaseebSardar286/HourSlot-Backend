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
public class BranchWorkingHour {

    private Long id;

    @JsonIgnoreProperties({"business", "hibernateLazyInitializer", "handler"})
    private Branch branch;

    @NotNull
    private int dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    private boolean closed;

    @JsonIgnoreProperties("workingHour")
    @Builder.Default
    private List<BranchBreak> breaks = new ArrayList<>();
}
