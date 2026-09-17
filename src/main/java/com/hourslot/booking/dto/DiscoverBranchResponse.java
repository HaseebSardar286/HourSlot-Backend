package com.hourslot.booking.dto;

import com.hourslot.organization.model.BusinessStatus;
import lombok.Data;

@Data
public class DiscoverBranchResponse {
    private Long id;
    private String name;
    private String address;
    private String phoneNumber;
    private String countryCode;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private Double distanceMeters;
    private DiscoverBusinessResponse business;

    @Data
    public static class DiscoverBusinessResponse {
        private Long id;
        private String name;
        private String slug;
        private String description;
        private String logoUrl;
        private String galleryUrls;
        private BusinessStatus status;
        private boolean verified;
        private String currency;
        private String countryCode;
        private String listingMode;
        private String serviceMode;
        private String opsStatus;
        private CategorySummary primaryCategory;
        private double rating;
    }

    @Data
    public static class CategorySummary {
        private Long id;
        private String name;
        private String slug;
    }
}
