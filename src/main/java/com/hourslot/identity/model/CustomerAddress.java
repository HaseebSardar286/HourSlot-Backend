package com.hourslot.identity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import com.hourslot.geo.model.GeoArea;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAddress {
    private Long id;
    private User customerUser;
    private String label;
    private String addressLine;
    private String countryCode;
    private String region;
    private String city;
    private String areaName;
    private String postalCode;
    private GeoArea geoArea;
    private Double latitude;
    private Double longitude;
    @Builder.Default
    private boolean isDefault = false;
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
