package com.hourslot.controller;

import com.hourslot.model.*;
import com.hourslot.repository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
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

    @Data
    public static class PublicBusinessProfile {
        private Business business;
        private List<Branch> branches;
        private List<Service> services;
        private List<Staff> staff;
        private List<Review> reviews;
        private double averageRating;
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> getNearbyBranches(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "50000") double radius) { // Radius in meters (50km default)

        List<Branch> branches = branchRepository.findNearbyBranches(lat, lon, radius).stream()
                .filter(this::isBookableBusiness)
                .collect(Collectors.toList());
        return ResponseEntity.ok(branches);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchBranches(@RequestParam(defaultValue = "") String q) {
        List<Branch> allBranches = branchRepository.findAll().stream()
                .filter(this::isBookableBusiness)
                .collect(Collectors.toList());

        if (q.trim().isEmpty()) {
            return ResponseEntity.ok(allBranches);
        }

        String query = q.toLowerCase().trim();
        List<Branch> filtered = allBranches.stream()
                .filter(b -> {
                    boolean matchName = b.getName().toLowerCase().contains(query);
                    boolean matchAddress = b.getAddress() != null && b.getAddress().toLowerCase().contains(query);
                    boolean matchBusiness = b.getBusiness().getName().toLowerCase().contains(query);

                    boolean matchCategory = false;
                    if (b.getBusiness().getPrimaryCategory() != null &&
                            b.getBusiness().getPrimaryCategory().getName().toLowerCase().contains(query)) {
                        matchCategory = true;
                    }

                    List<Service> services = serviceRepository.findByBusiness(b.getBusiness());
                    boolean matchService = services.stream().anyMatch(s -> s.getName().toLowerCase().contains(query));

                    return matchName || matchAddress || matchBusiness || matchCategory || matchService;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    private boolean isBookableBusiness(Branch branch) {
        Business business = branch.getBusiness();
        return business != null
                && business.getStatus() == BusinessStatus.APPROVED
                && business.isVerified();
    }

    @GetMapping("/categories")
    public ResponseEntity<?> getCategories() {
        // Return root categories
        List<Category> roots = categoryRepository.findByParentIsNullAndActiveTrue();
        return ResponseEntity.ok(roots);
    }

    @GetMapping("/business/{id}")
    public ResponseEntity<?> getPublicBusinessProfile(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (business.getStatus() != BusinessStatus.APPROVED || !business.isVerified()) {
            throw new RuntimeException("Business is not available for booking.");
        }

        List<Branch> branches = branchRepository.findByBusiness(business);
        List<Service> services = serviceRepository.findByBusiness(business);
        
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
        profile.setAverageRating(avgRating);

        return ResponseEntity.ok(profile);
    }
}
