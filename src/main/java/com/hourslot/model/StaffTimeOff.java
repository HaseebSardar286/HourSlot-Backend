package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class StaffTimeOff {

    private Long id;

    private Staff staff;

    @NotNull
    private LocalDateTime startAt;

    @NotNull
    private LocalDateTime endAt;

    private String reason;

    @Builder.Default
    private String status = "APPROVED";

    @JsonProperty("date")
    public LocalDate getDate() {
        return startAt == null ? null : startAt.toLocalDate();
    }

    @JsonProperty("description")
    public String getDescription() {
        return reason;
    }
}
