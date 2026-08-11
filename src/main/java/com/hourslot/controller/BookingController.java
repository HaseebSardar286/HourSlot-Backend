package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.BookingService;
import com.hourslot.service.BookingStatusRules;
import com.hourslot.service.NotificationService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private BookingStatusRules bookingStatusRules;

    @Data
    public static class BookingRequest {
        @NotNull
        private Long branchId;
        @NotNull
        private Long serviceId;
        private Long staffId;
        @NotNull
        private String bookingTime; // "yyyy-MM-dd'T'HH:mm:ss" or "yyyy-MM-dd HH:mm:ss"
        private Long customerId; // Optional: for Business Owner to book on behalf of client
        private String clientNotes;
    }

    @Data
    public static class StatusUpdate {
        @NotNull
        private String status; // CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED, IN_PROGRESS
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> createBooking(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Long customerIdToUse = null;

        if (user.getRole() == UserRole.CUSTOMER) {
            // Customer booking themselves
            customerIdToUse = user.getId();
        } else {
            // Business owner/staff booking on behalf of someone
            if (request.getCustomerId() == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: customerId is required for offline bookings."));
            }
            customerIdToUse = request.getCustomerId();
        }

        try {
            LocalDateTime bookingTime = LocalDateTime.parse(request.getBookingTime(), DateTimeFormatter.ISO_DATE_TIME);
            Booking booking = bookingService.createBooking(
                    customerIdToUse,
                    request.getBranchId(),
                    request.getServiceId(),
                    request.getStaffId(),
                    bookingTime,
                    request.getClientNotes()
            );
            User customerUser = userRepository.findById(customerIdToUse).orElse(null);
            notificationService.notify(
                    customerUser,
                    "Booking confirmed",
                    "Your appointment is confirmed for " + booking.getBookingTime() + "."
            );
            Branch bookedBranch = branchRepository.findById(request.getBranchId()).orElse(null);
            if (bookedBranch != null && bookedBranch.getBusiness() != null
                    && bookedBranch.getBusiness().getOwner() != null) {
                notificationService.notify(
                        bookedBranch.getBusiness().getOwner(),
                        "New booking",
                        "A new booking was created for " + bookingTime + "."
                );
            }
            return ResponseEntity.ok(booking);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Booking failed: " + e.getMessage()));
        }
    }

    @GetMapping("/bookings/branch/{branchId}")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> getBranchBookings(
            @PathVariable Long branchId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        if (user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = businessRepository.findByOwner(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (!branch.getBusiness().getId().equals(business.getId())) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
            }
        } else if (user.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access for staff."));
            }
        }

        List<Booking> bookings = bookingRepository.findByBranchOrderByBookingTimeDesc(branch);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBookingDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        // Check Authorization
        boolean isAuthorized = false;
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            isAuthorized = true;
        } else if (user.getRole() == UserRole.CUSTOMER && booking.getCustomer().getId().equals(user.getId())) {
            isAuthorized = true;
        } else if (user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = businessRepository.findByOwner(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (booking.getBranch().getBusiness().getId().equals(business.getId())) {
                isAuthorized = true;
            }
        } else if (user.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (booking.getBranch().getId().equals(staff.getBranch().getId())) {
                isAuthorized = true;
            }
        }

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized access to this booking."));
        }

        return ResponseEntity.ok(booking);
    }

    @PutMapping("/bookings/{id}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateBookingStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdate request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        BookingStatus newStatus;
        try {
            newStatus = BookingStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Invalid booking status."));
        }

        // Check Authorization
        boolean isAuthorized = false;
        if (user.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = businessRepository.findByOwner(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (booking.getBranch().getBusiness().getId().equals(business.getId())) {
                isAuthorized = true;
            }
        } else if (user.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (booking.getBranch().getId().equals(staff.getBranch().getId())) {
                isAuthorized = true;
            }
        } else if (user.getRole() == UserRole.CUSTOMER && booking.getCustomer().getId().equals(user.getId())) {
            // Customer can ONLY transition to CANCELLED
            if (newStatus == BookingStatus.CANCELLED) {
                isAuthorized = true;
            } else {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Customers can only cancel their bookings."));
            }
        }

        if (!isAuthorized) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized status modification."));
        }

        if (!bookingStatusRules.isValidTransition(booking.getStatus(), newStatus, user.getRole())) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Error: Invalid status transition from " + booking.getStatus() + " to " + newStatus));
        }

        booking.setStatus(newStatus);
        bookingRepository.save(booking);
        notificationService.notify(
                booking.getCustomer() != null
                        ? userRepository.findById(booking.getCustomer().getId()).orElse(null)
                        : null,
                "Booking updated",
                "Your booking status is now " + newStatus + "."
        );

        return ResponseEntity.ok(new MessageResponse("Booking status updated to " + newStatus));
    }

    @GetMapping("/customer/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getCustomerBookings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Customer customer = customerRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        List<Booking> bookings = bookingRepository.findByCustomerOrderByBookingTimeDesc(customer);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/customer/bookings/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> cancelBookingByCustomer(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        if (!booking.getCustomer().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized booking cancellation."));
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        return ResponseEntity.ok(new MessageResponse("Booking cancelled successfully."));
    }

}
