package com.duemet.web.controller;

import com.duemet.booking.dto.DiscoverBranchResponse;
import com.duemet.booking.dto.DiscoverBranchResponse.CategorySummary;
import com.duemet.booking.dto.DiscoverBranchResponse.DiscoverBusinessResponse;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.catalog.model.Category;
import com.duemet.booking.model.Review;
import com.duemet.organization.model.Staff;
import com.duemet.catalog.model.StaffService;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.catalog.repository.CategoryRepository;
import com.duemet.booking.repository.ReviewRepository;
import com.duemet.catalog.repository.ServicePackageRepository;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.catalog.repository.StaffServiceRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/discover")
@CrossOrigin(origins = "*")
public class DiscoveryController {

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private com.duemet.organization.repository.BusinessServiceAreaRepository businessServiceAreaRepository;

    @Data
    public static class PublicBusinessProfile {
        private Business business;
        private List<Branch> branches;
        private List<com.duemet.catalog.model.Service> services;
        private List<Staff> staff;
        private List<Review> reviews;
        private List<com.duemet.catalog.model.ServicePackage> packages;
        private double averageRating;
    }

    @GetMapping("/nearby")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getNearbyBranches(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "50000") double radius,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String serviceMode,
            @RequestParam(required = false) String listingMode,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) Long geoAreaId) {
        return ResponseEntity.ok(findNearby(lat, lon, radius, q, serviceMode, listingMode, verified, geoAreaId));
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<?> searchBranches(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String serviceMode,
            @RequestParam(required = false) String listingMode,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) Long geoAreaId) {
        return ResponseEntity.ok(search(q, serviceMode, listingMode, verified, geoAreaId));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(categoryRepository.findActiveHierarchy());
    }

    @GetMapping("/business/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicBusinessProfile(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (business.getStatus() != BusinessStatus.APPROVED
                || !"PUBLISHED".equalsIgnoreCase(business.getPublishStatus())) {
            throw new RuntimeException("Business is not available for booking.");
        }
        if (business.getIndividualDisabledReason() != null
                && !business.getIndividualDisabledReason().isBlank()) {
            throw new RuntimeException("Business is not available for booking.");
        }

        if (business.getPrimaryCategory() != null) {
            business.getPrimaryCategory().getName();
        }

        List<Branch> branches = branchRepository.findByBusiness(business);
        List<com.duemet.catalog.model.Service> services = serviceRepository.findByBusiness(business);

        List<Staff> staff = staffRepository.findByBusiness(business);
        attachAllocatedServices(staff, business, services);

        List<Review> reviews = reviewRepository.findByBusinessOrderByCreatedAtDesc(business);

        double avgRating = 0.0;
        if (!reviews.isEmpty()) {
            avgRating = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
        }

        PublicBusinessProfile profile = new PublicBusinessProfile();
        profile.setBusiness(business);
        profile.setBranches(branches);
        profile.setServices(services);
        profile.setStaff(staff);
        profile.setReviews(reviews);
        profile.setPackages(servicePackageRepository.findByBusinessAndActiveTrue(business));
        profile.setAverageRating(avgRating);

        return ResponseEntity.ok(profile);
    }

    private List<DiscoverBranchResponse> findNearby(
            double lat, double lon, double radius, String q,
            String serviceMode, String listingMode, Boolean verified, Long geoAreaId) {
        List<Long> ids = branchRepository.findNearbyBranchIds(lat, lon, radius);
        if (ids == null) {
            ids = Collections.emptyList();
        }

        Map<Long, Branch> byId = ids.isEmpty()
                ? Collections.emptyMap()
                : branchRepository.findAllWithBusinessByIdIn(ids).stream()
                .filter(this::isBookable)
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (a, b) -> a));

        List<Branch> ordered = new ArrayList<>();
        java.util.Set<Long> seenBusinessIds = new java.util.HashSet<>();
        for (Long id : ids) {
            Branch branch = byId.get(id);
            if (branch != null && branch.getBusiness() != null) {
                ordered.add(branch);
                seenBusinessIds.add(branch.getBusiness().getId());
            }
        }

        // Solo / home-service listings often use service areas instead of a shop pin.
        List<Long> areaBusinessIds = businessServiceAreaRepository
                .findPublishedBusinessIdsCoveringPoint(lat, lon, radius);
        if (areaBusinessIds != null && !areaBusinessIds.isEmpty()) {
            List<Long> missing = areaBusinessIds.stream()
                    .filter(id -> !seenBusinessIds.contains(id))
                    .toList();
            for (Long businessId : missing) {
                Business business = businessRepository.findById(businessId).orElse(null);
                if (business == null) {
                    continue;
                }
                Branch representative = branchRepository.findByBusiness(business).stream()
                        .findFirst()
                        .orElse(null);
                if (representative == null) {
                    continue;
                }
                representative.setBusiness(business);
                if (!isBookable(representative)) {
                    continue;
                }
                ordered.add(representative);
                seenBusinessIds.add(businessId);
            }
        }

        if (ordered.isEmpty()) {
            return Collections.emptyList();
        }

        return applyDiscoveryFilters(ordered, q, serviceMode, listingMode, verified, geoAreaId).stream()
                .map(branch -> toResponse(branch, lat, lon))
                .collect(Collectors.toList());
    }

    private List<DiscoverBranchResponse> search(
            String q, String serviceMode, String listingMode, Boolean verified, Long geoAreaId) {
        List<Branch> branches = branchRepository.findAllWithBusiness().stream()
                .filter(this::isBookable)
                .collect(Collectors.toList());
        // One card per business for text search (avoid duplicate implicit + real branches).
        Map<Long, Branch> onePerBusiness = new java.util.LinkedHashMap<>();
        for (Branch branch : branches) {
            if (branch.getBusiness() == null) {
                continue;
            }
            Long bizId = branch.getBusiness().getId();
            Branch existing = onePerBusiness.get(bizId);
            if (existing == null || (isRealBranchPin(branch) && !isRealBranchPin(existing))) {
                onePerBusiness.put(bizId, branch);
            }
        }
        return applyDiscoveryFilters(new ArrayList<>(onePerBusiness.values()), q, serviceMode, listingMode, verified, geoAreaId)
                .stream()
                .map(branch -> toResponse(branch, null, null))
                .collect(Collectors.toList());
    }

    private List<Branch> applyDiscoveryFilters(
            List<Branch> branches,
            String q,
            String serviceMode,
            String listingMode,
            Boolean verified,
            Long geoAreaId) {
        List<Branch> filtered = filterByQuery(branches, q);
        if (serviceMode != null && !serviceMode.isBlank()) {
            String mode = serviceMode.trim().toUpperCase();
            filtered = filtered.stream()
                    .filter(branch -> branch.getBusiness() != null
                            && mode.equalsIgnoreCase(branch.getBusiness().getServiceMode()))
                    .collect(Collectors.toList());
        }
        if (listingMode != null && !listingMode.isBlank()) {
            String mode = listingMode.trim().toUpperCase();
            filtered = filtered.stream()
                    .filter(branch -> branch.getBusiness() != null
                            && mode.equalsIgnoreCase(branch.getBusiness().getListingMode()))
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(verified)) {
            filtered = filtered.stream()
                    .filter(branch -> branch.getBusiness() != null && branch.getBusiness().isVerified())
                    .collect(Collectors.toList());
        }
        if (geoAreaId != null) {
            java.util.Set<Long> ids = new java.util.HashSet<>(
                    businessServiceAreaRepository.findBusinessIdsByGeoArea(geoAreaId));
            filtered = filtered.stream()
                    .filter(branch -> branch.getBusiness() != null && ids.contains(branch.getBusiness().getId()))
                    .collect(Collectors.toList());
        }
        return filtered;
    }

    private List<Branch> filterByQuery(List<Branch> branches, String q) {
        if (q == null || q.trim().isEmpty()) {
            return branches;
        }
        if (branches.isEmpty()) {
            return branches;
        }

        Set<Long> businessIds = branches.stream()
                .map(Branch::getBusiness)
                .filter(Objects::nonNull)
                .map(Business::getId)
                .collect(Collectors.toSet());

        Map<Long, List<com.duemet.catalog.model.Service>> servicesByBusiness = businessIds.isEmpty()
                ? Collections.emptyMap()
                : serviceRepository.findByBusinessIdIn(businessIds).stream()
                .collect(Collectors.groupingBy(service -> service.getBusiness().getId()));

        String query = q.toLowerCase().trim();
        return branches.stream()
                .filter(branch -> matchesQuery(branch, query, servicesByBusiness))
                .collect(Collectors.toList());
    }

    private boolean matchesQuery(
            Branch branch,
            String query,
            Map<Long, List<com.duemet.catalog.model.Service>> servicesByBusiness) {
        if (branch.getName() != null && branch.getName().toLowerCase().contains(query)) {
            return true;
        }
        if (branch.getAddress() != null && branch.getAddress().toLowerCase().contains(query)) {
            return true;
        }
        if (branch.getCity() != null && branch.getCity().toLowerCase().contains(query)) {
            return true;
        }
        if (branch.getRegion() != null && branch.getRegion().toLowerCase().contains(query)) {
            return true;
        }
        if (branch.getCountryCode() != null && branch.getCountryCode().toLowerCase().contains(query)) {
            return true;
        }

        Business business = branch.getBusiness();
        if (business == null) {
            return false;
        }
        if (business.getName() != null && business.getName().toLowerCase().contains(query)) {
            return true;
        }
        if (matchesCategory(business.getPrimaryCategory(), query)) {
            return true;
        }
        if (business.getSecondaryCategories() != null) {
            for (Category category : business.getSecondaryCategories()) {
                if (matchesCategory(category, query)) {
                    return true;
                }
            }
        }

        List<com.duemet.catalog.model.Service> services = servicesByBusiness.getOrDefault(business.getId(), Collections.emptyList());
        return services.stream()
                .anyMatch(service -> service.getName() != null && service.getName().toLowerCase().contains(query));
    }

    private boolean matchesCategory(Category category, String query) {
        if (category == null) {
            return false;
        }
        if (category.getName() != null && category.getName().toLowerCase().contains(query)) {
            return true;
        }
        if (category.getSlug() != null && category.getSlug().toLowerCase().contains(query)) {
            return true;
        }
        return category.getSearchTags() != null && category.getSearchTags().toLowerCase().contains(query);
    }

    private boolean isBookable(Branch branch) {
        Business business = branch.getBusiness();
        if (business == null
                || business.getStatus() != BusinessStatus.APPROVED
                || !"PUBLISHED".equalsIgnoreCase(business.getPublishStatus())
                || (business.getIndividualDisabledReason() != null
                        && !business.getIndividualDisabledReason().isBlank())) {
            return false;
        }
        // Must be findable: a real branch pin and/or at least one active service area.
        if (isRealBranchPin(branch)) {
            return true;
        }
        return business.getId() != null
                && !businessServiceAreaRepository.findByBusinessId(business.getId()).isEmpty();
    }

    private boolean isRealBranchPin(Branch branch) {
        if (branch.getLatitude() == null || branch.getLongitude() == null) {
            return false;
        }
        return Math.abs(branch.getLatitude()) >= 0.0001 || Math.abs(branch.getLongitude()) >= 0.0001;
    }

    private DiscoverBranchResponse toResponse(Branch branch, Double userLat, Double userLon) {
        Business business = branch.getBusiness();

        DiscoverBusinessResponse businessDto = new DiscoverBusinessResponse();
        if (business != null) {
            businessDto.setId(business.getId());
            businessDto.setName(business.getName());
            businessDto.setSlug(business.getSlug());
            businessDto.setDescription(business.getDescription());
            businessDto.setLogoUrl(business.getLogoUrl());
            businessDto.setGalleryUrls(business.getGalleryUrls());
            businessDto.setStatus(business.getStatus());
            businessDto.setVerified(business.isVerified());
            businessDto.setCurrency(business.getCurrency());
            businessDto.setCountryCode(business.getCountryCode());
            businessDto.setListingMode(business.getListingMode());
            businessDto.setServiceMode(business.getServiceMode());
            businessDto.setOpsStatus(business.getOpsStatus());
            boolean online = business.isOnline()
                    && business.getLastSeenAt() != null
                    && business.getLastSeenAt().isAfter(java.time.LocalDateTime.now().minusMinutes(
                            com.duemet.organization.services.ProviderPresenceService.IDLE_TIMEOUT_MINUTES));
            businessDto.setIsOnline(online);
            businessDto.setRating(business.getRating());
            businessDto.setPrimaryCategory(toCategorySummary(business.getPrimaryCategory()));
        }

        DiscoverBranchResponse dto = new DiscoverBranchResponse();
        dto.setId(branch.getId());
        dto.setName(branch.getName());
        dto.setAddress(branch.getAddress());
        dto.setPhoneNumber(branch.getPhoneNumber());
        dto.setCountryCode(branch.getCountryCode());
        dto.setRegion(branch.getRegion());
        dto.setCity(branch.getCity());
        dto.setLatitude(branch.getLatitude());
        dto.setLongitude(branch.getLongitude());
        dto.setBusiness(businessDto);

        if (userLat != null && userLon != null
                && branch.getLatitude() != null && branch.getLongitude() != null) {
            dto.setDistanceMeters(haversineMeters(userLat, userLon, branch.getLatitude(), branch.getLongitude()));
        }
        return dto;
    }

    private CategorySummary toCategorySummary(Category category) {
        if (category == null) {
            return null;
        }
        CategorySummary summary = new CategorySummary();
        summary.setId(category.getId());
        summary.setName(category.getName());
        summary.setSlug(category.getSlug());
        return summary;
    }

    private void attachAllocatedServices(
            List<Staff> staff,
            Business business,
            List<com.duemet.catalog.model.Service> catalog) {
        if (staff == null || staff.isEmpty()) {
            return;
        }
        Map<Long, List<com.duemet.catalog.model.Service>> byStaff = new java.util.LinkedHashMap<>();
        for (StaffService mapping : staffServiceRepository.findByStaffBranchBusiness(business)) {
            if (mapping.getStaff() == null || mapping.getStaff().getId() == null || mapping.getService() == null) {
                continue;
            }
            com.duemet.catalog.model.Service allocated = new com.duemet.catalog.model.Service();
            allocated.setId(mapping.getService().getId());
            allocated.setName(mapping.getService().getName());
            byStaff.computeIfAbsent(mapping.getStaff().getId(), key -> new ArrayList<>()).add(allocated);
        }
        boolean hasMappings = !byStaff.isEmpty();
        for (Staff member : staff) {
            List<com.duemet.catalog.model.Service> allocated = byStaff.get(member.getId());
            if (allocated != null && !allocated.isEmpty()) {
                member.setAllocatedServices(allocated);
            } else if (!hasMappings) {
                List<com.duemet.catalog.model.Service> fallback = new ArrayList<>();
                for (com.duemet.catalog.model.Service service : catalog) {
                    com.duemet.catalog.model.Service copy = new com.duemet.catalog.model.Service();
                    copy.setId(service.getId());
                    copy.setName(service.getName());
                    fallback.add(copy);
                }
                member.setAllocatedServices(fallback);
            } else {
                member.setAllocatedServices(new ArrayList<>());
            }
        }
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadius * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
