package com.hourslot.organization.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.hourslot.geo.model.GeoArea;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessServiceArea {
    private Long id;
    private Business business;
    /** NAMED | RADIUS */
    private String coverageType;
    private GeoArea geoArea;
    private String areaName;
    private Double latitude;
    private Double longitude;
    private BigDecimal radiusKm;
    @Builder.Default
    private String status = "ACTIVE";
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
