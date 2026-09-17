package com.hourslot.community.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Community {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String countryCode;
    private String region;
    private String city;
    private String areaName;
    @Builder.Default
    private String status = "ACTIVE";
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
