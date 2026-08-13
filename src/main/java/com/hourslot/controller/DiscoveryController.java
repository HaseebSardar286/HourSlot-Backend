package com.hourslot.controller;

import com.hourslot.dto.DiscoverBranchResponse;
import com.hourslot.dto.DiscoverBranchResponse.CategorySummary;
import com.hourslot.dto.DiscoverBranchResponse.DiscoverBusinessResponse;
import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import com.hourslot.model.BusinessStatus;
import com.hourslot.model.Category;
import com.hourslot.model.Review;
import com.hourslot.model.Staff;
import com.hourslot.repository.BranchRepository;
import com.hourslot.repository.BusinessRepository;
import com.hourslot.repository.CategoryRepository;
import com.hourslot.repository.ReviewRepository;
import com.hourslot.repository.ServicePackageRepository;
import com.hourslot.repository.ServiceRepository;
import com.hourslot.repository.StaffRepository;
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

    @Data
    public static class PublicBusinessProfile {
        private Business business;
        private List<Branch> branches;
        private List<com.hourslot.model.Service> services;
        private List<Staff> staff;
        private List<Review> reviews;
        private List<com.hourslot.model.ServicePackage> packages;
        private double averageRating;
    }

    @GetMapping("/nearby")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getNearbyBranches(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "50000") double radius,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(findNearby(lat, lon, radius, q));
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public ResponseEntity<?> searchBranches(@RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(search(q));
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        List<Category> roots = categoryRepository.findByParentIsNullAndActiveTrue();
        return ResponseEntity.ok(roots);
    }

    @GetMapping("/business/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getPublicBusinessProfile(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (business.getStatus() != BusinessStatus.APPROVED || !business.isVerified()) {
            throw new RuntimeException("Business is not available for booking.");
        }

        if (business.getPrimaryCategory() != null) {
            business.getPrimaryCategory().getName();
        }

        List<Branch> branches = branchRepository.findByBusiness(business);
        List<com.hourslot.model.Service> services = serviceRepository.findByBusiness(business);

        List<Staff> staff = new ArrayList<>();
        for (Branch b : branches) {
            staff.addAll(staffRepository.findByBranch(b));
        }

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

    private List<DiscoverBranchResponse> findNearby(double lat, double lon, double radius, String q) {
        List<Long> ids = branchRepository.findNearbyBranchIds(lat, lon, radius);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Branch> byId = branchRepository.findAllWithBusinessByIdIn(ids).stream()
                .filter(this::isBookable)
                .collect(Collectors.toMap(Branch::getId, Function.identity(), (a, b) -> a));

        List<Branch> ordered = new ArrayList<>();
        for (Long id : ids) {
            Branch branch = byId.get(id);
            if (branch != null) {
                ordered.add(branch);
            }
        }

        return filterByQuery(ordered, q).stream()
                .map(branch -> toResponse(branch, lat, lon))
                .collect(Collectors.toList());
    }

    private List<DiscoverBranchResponse> search(String q) {
        List<Branch> branches = branchRepository.findAllWithBusiness().stream()
                .filter(this::isBookable)
                .collect(Collectors.toList());
        return filterByQuery(branches, q).stream()
                .map(branch -> toResponse(branch, null, null))
                .collect(Collectors.toList());
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

        Map<Long, List<com.hourslot.model.Service>> servicesByBusiness = businessIds.isEmpty()
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
            Map<Long, List<com.hourslot.model.Service>> servicesByBusiness) {
        if (branch.getName() != null && branch.getName().toLowerCase().contains(query)) {
            return true;
        }
        if (branch.getAddress() != null && branch.getAddress().toLowerCase().contains(query)) {
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

        List<com.hourslot.model.Service> services = servicesByBusiness.getOrDefault(business.getId(), Collections.emptyList());
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
        return business != null
                && business.getStatus() == BusinessStatus.APPROVED
                && business.isVerified();
    }

    private DiscoverBranchResponse toResponse(Branch branch, Double userLat, Double userLon) {
        Business business = branch.getBusiness();

        DiscoverBusinessResponse businessDto = new DiscoverBusinessResponse();
        if (business != null) {
            businessDto.setId(business.getId());
            businessDto.setName(business.getName());
            businessDto.setDescription(business.getDescription());
            businessDto.setLogoUrl(business.getLogoUrl());
            businessDto.setGalleryUrls(business.getGalleryUrls());
            businessDto.setStatus(business.getStatus());
            businessDto.setVerified(business.isVerified());
            businessDto.setPrimaryCategory(toCategorySummary(business.getPrimaryCategory()));
        }

        DiscoverBranchResponse dto = new DiscoverBranchResponse();
        dto.setId(branch.getId());
        dto.setName(branch.getName());
        dto.setAddress(branch.getAddress());
        dto.setPhoneNumber(branch.getPhoneNumber());
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
