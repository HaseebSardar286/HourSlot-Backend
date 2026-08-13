package com.hourslot.dto;

import com.hourslot.model.BusinessStatus;
import lombok.Data;

@Data
public class DiscoverBranchResponse {
    private Long id;
    private String name;
    private String address;
    private String phoneNumber;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
    private DiscoverBusinessResponse business;

    @Data
    public static class DiscoverBusinessResponse {
        private Long id;
        private String name;
        private String description;
        private String logoUrl;
        private String galleryUrls;
        private BusinessStatus status;
        private boolean verified;
        private CategorySummary primaryCategory;
    }

    @Data
    public static class CategorySummary {
        private Long id;
        private String name;
        private String slug;
    }
}
