package com.hourslot.organization.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Branch {

    private Long id;

    @JsonIgnoreProperties({
            "organization", "secondaryCategories", "primaryCategory",
            "hibernateLazyInitializer", "handler"
    })
    private Business business;

    @NotBlank
    private String name;

    @NotBlank
    private String address;

    @NotNull
    private Double latitude;

    @NotNull
    private Double longitude;

    private String phoneNumber;

    private String countryCode;

    private String region;

    private String city;

    private String postalCode;

    private String timezone;

    @Builder.Default
    private boolean active = true;

    /** Hidden default location for individual providers. */
    @Builder.Default
    private boolean implicit = false;

    @Builder.Default
    private int sortOrder = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
