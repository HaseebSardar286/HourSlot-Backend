package com.duemet.organization.services;

import com.duemet.availability.repository.BranchWorkingHourRepository;
import com.duemet.availability.repository.StaffWorkingHourRepository;
import com.duemet.booking.repository.ReviewRepository;
import com.duemet.catalog.model.Category;
import com.duemet.catalog.repository.CategoryRepository;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessServiceArea;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.organization.model.BusinessVerificationDocument;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final VerificationDocumentService verificationDocumentService;
    private final BranchWorkingHourRepository branchWorkingHourRepository;
    private final StaffWorkingHourRepository staffWorkingHourRepository;
    private final JdbcSupport jdbc;

    public ProviderListingService(
            BusinessRepository businessRepository,
            CategoryRepository categoryRepository,
            BranchRepository branchRepository,
            StaffRepository staffRepository,
            ServiceRepository serviceRepository,
            ServiceAreaService serviceAreaService,
            ReviewRepository reviewRepository,
            VerificationDocumentService verificationDocumentService,
            BranchWorkingHourRepository branchWorkingHourRepository,
            StaffWorkingHourRepository staffWorkingHourRepository,
            JdbcSupport jdbc) {
        this.businessRepository = businessRepository;
        this.categoryRepository = categoryRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.serviceAreaService = serviceAreaService;
        this.reviewRepository = reviewRepository;
        this.verificationDocumentService = verificationDocumentService;
        this.branchWorkingHourRepository = branchWorkingHourRepository;
        this.staffWorkingHourRepository = staffWorkingHourRepository;
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
        return updateProfile(business, name, slug, description, serviceMode, yearsExperience, phone,
                primaryCategoryId, null);
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
            Long primaryCategoryId,
            String onboardingState) {
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
        if (onboardingState != null && !onboardingState.isBlank()) {
            business.setOnboardingState(onboardingState.trim().toUpperCase(Locale.ROOT));
        }
        return businessRepository.save(business);
    }

    /** Human-readable blockers that prevent going live on Explore. */
    @Transactional(readOnly = true)
    public List<String> publishBlockers(Business business) {
        List<String> blockers = new ArrayList<>();
        if (business.getStatus() != BusinessStatus.APPROVED) {
            blockers.add("Listing must be approved by the platform before it can go live.");
        }
        boolean hasActiveService = serviceRepository.findByBusiness(business).stream()
                .anyMatch(com.duemet.catalog.model.Service::isActive);
        if (!hasActiveService) {
            blockers.add("Add at least one active service.");
        }
        if (!hasWorkingHours(business)) {
            blockers.add("Set working hours so customers can book a time.");
        }
        if (!hasDiscoverableLocation(business)) {
            blockers.add("Add a real branch location or a service area so customers can find you.");
        }
        return blockers;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publishReadiness(Business business) {
        List<String> blockers = publishBlockers(business);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ready", blockers.isEmpty());
        result.put("blockers", blockers);
        result.put("status", business.getStatus() == null ? null : business.getStatus().name());
        result.put("publishStatus", business.getPublishStatus());
        result.put("verified", business.isVerified());
        result.put("live", business.getStatus() == BusinessStatus.APPROVED
                && "PUBLISHED".equalsIgnoreCase(business.getPublishStatus()));
        return result;
    }

    @Transactional
    public Business publish(Business business) {
        List<String> blockers = publishBlockers(business);
        if (!blockers.isEmpty()) {
            throw new IllegalStateException(String.join(" ", blockers));
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

    /**
     * Owner sends a rejected listing back to the review queue after re-uploading
     * any documents that were rejected.
     */
    @Transactional
    public Business resubmitForReview(Business business) {
        if (business.getStatus() != BusinessStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected listing can be resubmitted for review.");
        }
        List<BusinessVerificationDocument> docs = verificationDocumentService.list(business);
        // Every rejected type must have a newer SUBMITTED or APPROVED document as the latest.
        Map<String, BusinessVerificationDocument> latestByType = new LinkedHashMap<>();
        for (BusinessVerificationDocument doc : docs) {
            latestByType.putIfAbsent(doc.getDocumentType(), doc);
        }
        List<String> stillRejected = latestByType.values().stream()
                .filter(d -> BusinessVerificationDocument.STATUS_REJECTED.equals(d.getStatus()))
                .map(BusinessVerificationDocument::getDocumentType)
                .toList();
        if (!stillRejected.isEmpty()) {
            throw new IllegalStateException(
                    "Re-upload the rejected documents before submitting for review again: "
                            + String.join(", ", stillRejected) + ".");
        }
        business.setStatus(BusinessStatus.PENDING);
        business.setPublishStatus("DRAFT");
        business.setRejectionReason(null);
        business.setVerified(false);
        return businessRepository.save(business);
    }

    private boolean hasWorkingHours(Business business) {
        List<Branch> branches = branchRepository.findByBusiness(business);
        for (Branch branch : branches) {
            if (branchWorkingHourRepository.findByBranch(branch).stream()
                    .anyMatch(h -> !h.isClosed())) {
                return true;
            }
        }
        List<Staff> staff = staffRepository.findByBusiness(business);
        for (Staff member : staff) {
            if (staffWorkingHourRepository.findByStaff(member).stream()
                    .anyMatch(h -> !h.isClosed())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDiscoverableLocation(Business business) {
        List<BusinessServiceArea> areas = serviceAreaService.list(business);
        if (areas != null && !areas.isEmpty()) {
            return true;
        }
        return branchRepository.findByBusiness(business).stream()
                .anyMatch(this::isRealBranchPin);
    }

    /** Ignore the implicit solo placeholder at 0,0. */
    private boolean isRealBranchPin(Branch branch) {
        if (branch.getLatitude() == null || branch.getLongitude() == null) {
            return false;
        }
        if (Math.abs(branch.getLatitude()) < 0.0001 && Math.abs(branch.getLongitude()) < 0.0001) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findPublicBySlug(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .filter(b -> b.getStatus() == BusinessStatus.APPROVED)
                .filter(b -> "PUBLISHED".equalsIgnoreCase(b.getPublishStatus()))
                .filter(b -> b.getIndividualDisabledReason() == null
                        || b.getIndividualDisabledReason().isBlank())
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
        result.put("opsStatus", business.getOpsStatus());
        result.put("availableNow", business.isAvailableNow());
        result.put("availableNowUntil", business.isAvailableNow() ? business.getAvailableNowUntil() : null);
        boolean online = business.isOnline()
                && business.getLastSeenAt() != null
                && business.getLastSeenAt().isAfter(java.time.LocalDateTime.now().minusMinutes(
                        ProviderPresenceService.IDLE_TIMEOUT_MINUTES));
        result.put("isOnline", online);
        result.put("category", business.getPrimaryCategory());
        result.put("services", serviceRepository.findByBusiness(business).stream()
                .filter(com.duemet.catalog.model.Service::isActive)
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

    private Map<String, Object> publicService(com.duemet.catalog.model.Service service) {
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
