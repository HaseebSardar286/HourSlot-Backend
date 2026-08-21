package com.hourslot.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ServicePackage {

    private Long id;

    private Business business;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    @Min(0)
    private double price;

    @Builder.Default
    private String currency = "USD";

    @NotNull
    @Min(1)
    private int sessionsCount;

    private int expiryDays;

    @Builder.Default
    private boolean active = true;

    private List<Service> services;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
