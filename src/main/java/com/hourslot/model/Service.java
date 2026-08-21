package com.hourslot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Service {

    private Long id;

    private Business business;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private BigDecimal basePrice;

    @Builder.Default
    private String currency = "USD";

    @NotNull
    @Min(1)
    private int durationMinutes;

    @Builder.Default
    private int bufferMinutes = 0;

    @Builder.Default
    private int maxConcurrent = 1;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    private int capacity = 1;

    @Builder.Default
    private boolean groupService = false;

    @Builder.Default
    private int sortOrder = 0;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @JsonProperty("price")
    public double getPrice() {
        return basePrice == null ? 0.0 : basePrice.doubleValue();
    }

    public void setPrice(double price) {
        this.basePrice = BigDecimal.valueOf(price);
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.currency == null) {
            this.currency = "USD";
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
