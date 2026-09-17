package com.hourslot.geo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeoArea {
    private Long id;
    private String countryCode;
    private String region;
    private String city;
    private String name;
    private String slug;
    private Double latitude;
    private Double longitude;
    @Builder.Default
    private String status = "ACTIVE";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
