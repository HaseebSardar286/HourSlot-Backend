package com.duemet.web.controller;

import com.duemet.geo.model.GeoArea;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessServiceArea;
import com.duemet.organization.services.AuditService;
import com.duemet.organization.services.FeatureFlagService;
import com.duemet.organization.services.ProviderListingService;
import com.duemet.organization.services.ProviderPresenceService;
import com.duemet.organization.services.ServiceAreaService;
import com.duemet.organization.services.TenancyService;
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
    private final ProviderPresenceService providerPresenceService;

    public ProviderController(
            UserRepository userRepository,
            TenancyService tenancyService,
            FeatureFlagService featureFlagService,
            ProviderListingService providerListingService,
            ServiceAreaService serviceAreaService,
            AuditService auditService,
            ProviderPresenceService providerPresenceService) {
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.featureFlagService = featureFlagService;
        this.providerListingService = providerListingService;
        this.serviceAreaService = serviceAreaService;
        this.auditService = auditService;
        this.providerPresenceService = providerPresenceService;
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
        private String onboardingState;
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

    @Data
    public static class PresenceRequest {
        private Boolean online;
        private Double latitude;
        private Double longitude;
        private String source;
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
                request.getPrimaryCategoryId(), request.getOnboardingState()));
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

    @GetMapping("/api/provider/publish-readiness")
    public ResponseEntity<?> publishReadiness(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerListingService.publishReadiness(requireBusiness(userDetails)));
    }

    @PostMapping("/api/provider/publish")
    public ResponseEntity<?> publish(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = requireUser(userDetails);
        Business business = requireBusiness(userDetails);
        if (TenancyService.isIndividualDisabled(business)) {
            return ResponseEntity.status(403).body(java.util.Map.of(
                    "message", "Disabled individual listings cannot be published."));
        }
        try {
            business = providerListingService.publish(business);
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
        auditService.log(user, business, "PUBLISH_PROVIDER", "Business", business.getId(), business.getSlug());
        return ResponseEntity.ok(business);
    }

    @PostMapping("/api/provider/unpublish")
    public ResponseEntity<?> unpublish(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerListingService.unpublish(requireBusiness(userDetails)));
    }

    @PostMapping("/api/provider/resubmit")
    public ResponseEntity<?> resubmit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireBusiness(userDetails);
        try {
            Business updated = providerListingService.resubmitForReview(business);
            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Listing submitted for review again.",
                    "business", updated));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/api/provider/presence")
    public ResponseEntity<?> getPresence(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireBusiness(userDetails);
        providerPresenceService.markOfflineIfStale(business);
        return ResponseEntity.ok(providerPresenceService.snapshot(business));
    }

    @PostMapping("/api/provider/presence")
    public ResponseEntity<?> updatePresence(
            @RequestBody PresenceRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireBusiness(userDetails);
        boolean online = request.getOnline() == null || Boolean.TRUE.equals(request.getOnline());
        return ResponseEntity.ok(providerPresenceService.updatePresence(
                business, online, request.getLatitude(), request.getLongitude(), request.getSource()));
    }

    @GetMapping("/api/providers/{slug}")
    public ResponseEntity<?> publicProvider(@PathVariable String slug) {
        return ResponseEntity.ok(providerListingService.findPublicBySlug(slug));
    }

    private User requireUser(CustomUserDetails userDetails) {
        return userRepository.findById(userDetails.getId()).orElseThrow();
    }

    private Business requireBusiness(CustomUserDetails userDetails) {
        return tenancyService.requireIndividualProvider(requireUser(userDetails));
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
