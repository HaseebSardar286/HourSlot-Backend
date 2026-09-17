package com.hourslot.organization.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.hourslot.catalog.model.Service;
import com.hourslot.identity.model.User;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Staff {

    private Long id;

    private Branch branch;

    @JsonIgnoreProperties({"passwordHash", "hibernateLazyInitializer", "handler"})
    private User user;

    @NotBlank
    private String displayName;

    private String designation;

    private String specialty;

    private String bio;

    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Builder.Default
    private boolean active = true;

    /** Auto-created performer for individual providers. */
    @Builder.Default
    private boolean implicit = false;

    @Builder.Default
    private int sortOrder = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @JsonProperty("services")
    @Builder.Default
    private List<Service> allocatedServices = new ArrayList<>();

    @JsonProperty("name")
    public String getName() {
        return displayName;
    }

    public void setName(String name) {
        this.displayName = name;
    }

    @JsonProperty("rating")
    public double getRating() {
        return ratingAvg == null ? 0.0 : ratingAvg.doubleValue();
    }

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.ratingAvg == null) {
            this.ratingAvg = BigDecimal.ZERO;
        }
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
