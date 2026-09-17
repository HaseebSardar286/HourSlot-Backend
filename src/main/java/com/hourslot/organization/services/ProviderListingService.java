package com.hourslot.organization.services;

import com.hourslot.booking.repository.ReviewRepository;
import com.hourslot.catalog.model.Category;
import com.hourslot.catalog.repository.CategoryRepository;
import com.hourslot.catalog.repository.ServiceRepository;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.model.BusinessServiceArea;
import com.hourslot.organization.model.BusinessStatus;
import com.hourslot.organization.repository.BranchRepository;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.repository.StaffRepository;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProviderListingService {
    private final BusinessRepository businessRepository;
    private final CategoryRepository categoryRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceAreaService serviceAreaService;
    private final ReviewRepository reviewRepository;
    private final JdbcSupport jdbc;

    public ProviderListingService(
            BusinessRepository businessRepository,
            CategoryRepository categoryRepository,
            BranchRepository branchRepository,
            StaffRepository staffRepository,
            ServiceRepository serviceRepository,
            ServiceAreaService serviceAreaService,
            ReviewRepository reviewRepository,
            JdbcSupport jdbc) {
        this.businessRepository = businessRepository;
        this.categoryRepository = categoryRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.serviceAreaService = serviceAreaService;
        this.reviewRepository = reviewRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(Business business) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("business", businessRepository.findById(business.getId()).orElse(business));
        profile.put("serviceAreas", serviceAreaService.list(business));
        return profile;
    }

    @Transactional
    public Business updateProfile(
            Business business,
            String name,
            String slug,
            String description,
            String serviceMode,
            Integer yearsExperience,
            String phone,
            Long primaryCategoryId) {
        if (name != null && !name.isBlank()) {
            business.setName(name.trim());
        }
        if (slug != null) {
            String normalized = slug.trim().toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("Slug must contain lowercase letters, numbers, and hyphens.");
            }
            businessRepository.findBySlug(normalized)
                    .filter(other -> !other.getId().equals(business.getId()))
                    .ifPresent(other -> {
                        throw new IllegalStateException("Provider slug is already in use.");
                    });
            business.setSlug(normalized);
        }
        if (description != null) business.setDescription(description);
        if (serviceMode != null) business.setServiceMode(serviceMode.trim().toUpperCase(Locale.ROOT));
        if (yearsExperience != null && yearsExperience < 0) {
            throw new IllegalArgumentException("Years of experience cannot be negative.");
        }
        if (yearsExperience != null) business.setYearsExperience(yearsExperience);
        if (phone != null) business.setPhone(phone);
        if (primaryCategoryId != null) {
            Category category = categoryRepository.findById(primaryCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Primary category not found."));
            business.setPrimaryCategory(category);
        }
        return businessRepository.save(business);
    }

    @Transactional
    public Business publish(Business business) {
        if (business.getStatus() != BusinessStatus.APPROVED) {
            throw new IllegalStateException("Provider must be approved before it can be published.");
        }
        business.setPublishStatus("PUBLISHED");
        business.setOnboardingState("PUBLISHED");
        return businessRepository.save(business);
    }

    @Transactional
    public Business unpublish(Business business) {
        business.setPublishStatus("UNPUBLISHED");
        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findPublicBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .filter(b -> b.getStatus() == BusinessStatus.APPROVED)
                .filter(b -> "PUBLISHED".equalsIgnoreCase(b.getPublishStatus()))
                .orElseThrow(() -> new RuntimeException("Provider not found."));
        return assemblePublicMap(business);
    }

    public Map<String, Object> assemblePublicMap(Business business) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", business.getId());
        result.put("name", business.getName());
        result.put("slug", business.getSlug());
        result.put("bio", business.getDescription());
        result.put("listingMode", business.getListingMode());
        result.put("providerType", business.getProviderType());
        result.put("serviceMode", business.getServiceMode());
        result.put("publishStatus", business.getPublishStatus());
        result.put("yearsExperience", business.getYearsExperience());
        result.put("verified", business.isVerified());
        result.put("rating", business.getRating());
        result.put("logoUrl", business.getLogoUrl());
        result.put("galleryUrls", business.getGalleryUrls());
        result.put("category", business.getPrimaryCategory());
        result.put("services", serviceRepository.findByBusiness(business).stream()
                .filter(com.hourslot.catalog.model.Service::isActive)
                .map(this::publicService)
                .toList());
        result.put("staff", staffRepository.findByBusiness(business).stream()
                .map(staff -> {
                    Map<String, Object> member = new LinkedHashMap<>();
                    member.put("id", staff.getId());
                    member.put("displayName", staff.getDisplayName());
                    member.put("designation", staff.getDesignation());
                    member.put("specialty", staff.getSpecialty());
                    member.put("bio", staff.getBio());
                    member.put("rating", staff.getRating());
                    return member;
                })
                .toList());
        result.put("locations", branchRepository.findByBusiness(business).stream()
                .map(branch -> {
                    Map<String, Object> loc = new LinkedHashMap<>();
                    loc.put("id", branch.getId());
                    loc.put("name", branch.getName());
                    loc.put("city", branch.getCity() == null ? "" : branch.getCity());
                    loc.put("region", branch.getRegion() == null ? "" : branch.getRegion());
                    loc.put("countryCode", branch.getCountryCode() == null ? "" : branch.getCountryCode());
                    loc.put("implicit", branch.isImplicit());
                    return loc;
                })
                .toList());
        branchRepository.findByBusiness(business).stream().findFirst().ifPresent(branch -> {
            result.put("primaryBranchId", branch.getId());
        });
        List<Map<String, Object>> areaNames = serviceAreaService.list(business).stream()
                .map(this::publicArea)
                .toList();
        result.put("serviceAreas", areaNames);
        result.put("reviews", reviewRepository.findByBusinessOrderByCreatedAtDesc(business).stream()
                .limit(8)
                .map(review -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", review.getId());
                    row.put("rating", review.getRating());
                    row.put("comment", review.getComment());
                    row.put("createdAt", review.getCreatedAt());
                    return row;
                })
                .toList());
        long jobsCompleted = jdbc.count("""
                SELECT COUNT(*) FROM jobs WHERE provider_id = :id AND UPPER(status) = 'COMPLETED'
                """, jdbc.params().addValue("id", business.getId()));
        long bookingsCompleted = jdbc.count("""
                SELECT COUNT(*) FROM bookings
                WHERE business_id = :id AND deleted_at IS NULL AND UPPER(status) = 'COMPLETED'
                """, jdbc.params().addValue("id", business.getId()));
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rating", business.getRating());
        metrics.put("reviewCount", business.getRatingCount());
        metrics.put("jobsCompleted", jobsCompleted);
        metrics.put("bookingsCompleted", bookingsCompleted);
        metrics.put("yearsExperience", business.getYearsExperience());
        metrics.put("verified", business.isVerified());
        result.put("metrics", metrics);
        return result;
    }

    private Map<String, Object> publicArea(BusinessServiceArea area) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", area.getCoverageType());
        row.put("name", area.getAreaName() != null
                ? area.getAreaName()
                : area.getGeoArea() == null ? null : area.getGeoArea().getName());
        return row;
    }

    private Map<String, Object> publicService(com.hourslot.catalog.model.Service service) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", service.getId());
        row.put("name", service.getName());
        row.put("description", service.getDescription());
        row.put("price", service.getPrice());
        row.put("basePrice", service.getBasePrice());
        row.put("currency", service.getCurrency());
        row.put("durationMinutes", service.getDurationMinutes());
        row.put("pricingType", service.getPricingType());
        row.put("requiresQuote", service.isRequiresQuote()
                || "QUOTE".equalsIgnoreCase(service.getPricingType()));
        row.put("serviceMode", service.getServiceMode());
        row.put("allowsHomeService", service.isAllowsHomeService());
        return row;
    }
}
