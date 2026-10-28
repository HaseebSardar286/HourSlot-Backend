package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.booking.model.Booking;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.model.BookingStatus;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.booking.model.Favorite;
import com.duemet.booking.repository.FavoriteRepository;
import com.duemet.booking.model.Review;
import com.duemet.booking.repository.ReviewRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.job.model.Job;
import com.duemet.job.repository.JobRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private JobRepository jobRepository;

    @Data
    public static class ReviewRequest {
        private Long bookingId;
        private Long jobId;
        
        @Min(1)
        @Max(5)
        private int rating;
        
        private String comment;
    }

    @PostMapping("/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> submitReview(
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking;
        Long jobId = request.getJobId();
        if (jobId != null) {
            Job job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found."));
            if (!job.getCustomerId().equals(userDetails.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized to review this job."));
            }
            if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Only completed jobs can be reviewed."));
            }
            if (reviewRepository.existsByJobId(jobId)
                    || (job.getBookingId() != null && reviewRepository.existsByBooking(Booking.builder().id(job.getBookingId()).build()))) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: A review has already been submitted."));
            }
            if (job.getBookingId() == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: Job is not linked to a booking."));
            }
            booking = bookingRepository.findByIdWithDetails(job.getBookingId())
                    .orElseThrow(() -> new RuntimeException("Booking not found."));
        } else {
            if (request.getBookingId() == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: bookingId or jobId is required."));
            }
            booking = bookingRepository.findByIdWithDetails(request.getBookingId())
                    .orElseThrow(() -> new RuntimeException("Booking not found."));
        }

        // 1. Check ownership
        if (!booking.getCustomer().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized to review this booking."));
        }

        // 2. Check booking status (must be COMPLETED)
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Only completed bookings can be reviewed."));
        }

        // 3. Check if already reviewed
        if (reviewRepository.existsByBooking(booking)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: A review has already been submitted for this booking."));
        }

        // 4. Save review
        Business reviewBusiness = booking.resolvedBusiness();
        if (reviewBusiness == null) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Booking is not linked to a business."));
        }

        Review review = Review.builder()
                .customerUser(booking.getCustomerUser())
                .business(reviewBusiness)
                .booking(booking)
                .jobId(jobId)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        reviewRepository.save(review);
        return ResponseEntity.ok(new MessageResponse("Review submitted successfully!"));
    }

    @GetMapping("/reviews/business/{id}")
    public ResponseEntity<?> getBusinessReviews(@PathVariable Long id) {
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        List<Review> reviews = reviewRepository.findByBusinessOrderByCreatedAtDesc(business);
        return ResponseEntity.ok(reviews);
    }

    @PostMapping("/favorites/{businessId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> addFavorite(
            @PathVariable Long businessId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User customer = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        if (favoriteRepository.existsByCustomerUserAndBusiness(customer, business)) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Business is already in your favorites."));
        }

        Favorite favorite = Favorite.builder()
                .customerUser(customer)
                .business(business)
                .build();

        favoriteRepository.save(favorite);
        return ResponseEntity.ok(new MessageResponse("Business added to favorites."));
    }

    @DeleteMapping("/favorites/{businessId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> removeFavorite(
            @PathVariable Long businessId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User customer = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found."));

        Optional<Favorite> favoriteOpt = favoriteRepository.findByCustomerUserAndBusiness(customer, business);
        if (favoriteOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Business is not in your favorites."));
        }

        favoriteRepository.delete(favoriteOpt.get());
        return ResponseEntity.ok(new MessageResponse("Business removed from favorites."));
    }

    @GetMapping("/favorites")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getCustomerFavorites(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User customer = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        List<Favorite> favorites = favoriteRepository.findByCustomerUser(customer);
        return ResponseEntity.ok(favorites);
    }
}
