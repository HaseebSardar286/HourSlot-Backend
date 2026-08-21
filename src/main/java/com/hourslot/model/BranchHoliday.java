package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class BranchHoliday {

    private Long id;

    private Branch branch;

    @NotNull
    private LocalDate holidayDate;

    private String description;

    @JsonProperty("date")
    public LocalDate getDate() {
        return holidayDate;
    }

    public void setDate(LocalDate date) {
        this.holidayDate = date;
    }
}
