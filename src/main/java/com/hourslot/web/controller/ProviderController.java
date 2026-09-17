package com.hourslot.web.controller;

import com.hourslot.geo.model.GeoArea;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessServiceArea;
import com.hourslot.organization.services.AuditService;
import com.hourslot.organization.services.FeatureFlagService;
import com.hourslot.organization.services.ProviderListingService;
import com.hourslot.organization.services.ServiceAreaService;
import com.hourslot.organization.services.TenancyService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
public class ProviderController {
    private final UserRepository userRepository;
    private final TenancyService tenancyService;
    private final FeatureFlagService featureFlagService;
    private final ProviderListingService providerListingService;
    private final ServiceAreaService serviceAreaService;
    private final AuditService auditService;

    public ProviderController(
            UserRepository userRepository,
            TenancyService tenancyService,
            FeatureFlagService featureFlagService,
            ProviderListingService providerListingService,
            ServiceAreaService serviceAreaService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.featureFlagService = featureFlagService;
        this.providerListingService = providerListingService;
        this.serviceAreaService = serviceAreaService;
        this.auditService = auditService;
    }

    @Data
    public static class IndividualRequest {
        private String displayName;
        private String countryCode;
        private String region;
        private String city;
        private String timezone;
        private String currency;
    }

    @Data
    public static class ProfileRequest {
        private String name;
        private String slug;
        private String bio;
        private String serviceMode;
        private Integer yearsExperience;
        private String phone;
        private Long primaryCategoryId;
    }

    @Data
    public static class ServiceAreaRequest {
        private String coverageType;
        private Long geoAreaId;
        private String areaName;
        private Double latitude;
        private Double longitude;
        private BigDecimal radiusKm;
        private String status;
    }

    @PostMapping("/api/provider/individual")
    public ResponseEntity<?> provisionIndividual(
            @RequestBody IndividualRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!featureFlagService.isEnabled("individual_providers")) {
            return ResponseEntity.status(403).body(java.util.Map.of(
                    "code", "FEATURE_NOT_AVAILABLE",
                    "message", "Individual provider registration is not currently available."));
        }
        User user = requireUser(userDetails);
        return ResponseEntity.ok(tenancyService.provisionIndividualProvider(
                user, request.getDisplayName(), request.getCountryCode(), request.getRegion(),
                request.getCity(), request.getTimezone(), request.getCurrency()));
    }

    @GetMapping("/api/provider/profile")
    public ResponseEntity<?> profile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerListingService.getProfile(requireBusiness(userDetails)));
    }

    @PutMapping("/api/provider/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody ProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerListingService.updateProfile(
                requireBusiness(userDetails), request.getName(), request.getSlug(), request.getBio(),
                request.getServiceMode(), request.getYearsExperience(), request.getPhone(),
                request.getPrimaryCategoryId()));
    }

    @GetMapping("/api/provider/service-areas")
    public ResponseEntity<?> serviceAreas(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceAreaService.list(requireBusiness(userDetails)));
    }

    @PostMapping("/api/provider/service-areas")
    public ResponseEntity<?> createServiceArea(
            @RequestBody ServiceAreaRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceAreaService.create(requireBusiness(userDetails), toArea(request)));
    }

    @PutMapping("/api/provider/service-areas/{id}")
    public ResponseEntity<?> updateServiceArea(
            @PathVariable Long id,
            @RequestBody ServiceAreaRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceAreaService.update(requireBusiness(userDetails), id, toArea(request)));
    }

    @DeleteMapping("/api/provider/service-areas/{id}")
    public ResponseEntity<?> deleteServiceArea(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        serviceAreaService.delete(requireBusiness(userDetails), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/provider/publish")
    public ResponseEntity<?> publish(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = requireUser(userDetails);
        Business business = providerListingService.publish(requireBusiness(userDetails));
        auditService.log(user, business, "PUBLISH_PROVIDER", "Business", business.getId(), business.getSlug());
        return ResponseEntity.ok(business);
    }

    @PostMapping("/api/provider/unpublish")
    public ResponseEntity<?> unpublish(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerListingService.unpublish(requireBusiness(userDetails)));
    }

    @GetMapping("/api/providers/{slug}")
    public ResponseEntity<?> publicProvider(@PathVariable String slug) {
        return ResponseEntity.ok(providerListingService.findPublicBySlug(slug));
    }

    private User requireUser(CustomUserDetails userDetails) {
        return userRepository.findById(userDetails.getId()).orElseThrow();
    }

    private Business requireBusiness(CustomUserDetails userDetails) {
        return tenancyService.requireBusinessForUser(requireUser(userDetails));
    }

    private static BusinessServiceArea toArea(ServiceAreaRequest request) {
        return BusinessServiceArea.builder()
                .coverageType(request.getCoverageType())
                .geoArea(request.getGeoAreaId() == null ? null : GeoArea.builder().id(request.getGeoAreaId()).build())
                .areaName(request.getAreaName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusKm(request.getRadiusKm())
                .status(request.getStatus() == null ? "ACTIVE" : request.getStatus())
                .build();
    }
}
