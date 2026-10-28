package com.duemet.web.controller;

import com.duemet.identity.dto.MessageResponse;
import com.duemet.booking.model.Booking;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.services.BookingService;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.services.BookingStatusRules;
import com.duemet.organization.model.Branch;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.catalog.model.CustomerPackage;
import com.duemet.catalog.repository.CustomerPackageRepository;
import com.duemet.notification.services.MailService;
import com.duemet.notification.services.NotificationService;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.organization.services.TenancyService;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.model.UserRole;
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
    private TenancyService tenancyService;

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
    private CustomerPackageRepository customerPackageRepository;

    @Autowired
    private BookingStatusRules bookingStatusRules;

    @Autowired
    private com.duemet.booking.services.BookingPolicyService bookingPolicyService;

    @Autowired
    private com.duemet.booking.repository.BookingIncidentRepository bookingIncidentRepository;

    @Autowired
    private MailService mailService;

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
        private Long customerPackageId;
        /** ONLINE | VENUE | PACKAGE — ONLINE creates PENDING until Stripe webhook confirms. */
        private String paymentMethod;
        /** Customer address for home-visit services (provider travels to the customer). */
        private Long addressId;
        /** Book against the provider's "available now" window; the service's urgent surcharge applies. */
        private boolean urgent;
        /** Optional business promo code. */
        private String promoCode;
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

        if (userDetails.getRole() == UserRole.CUSTOMER) {
            // Customer booking themselves
            customerIdToUse = user.getId();
        } else {
            // Business owner/staff booking on behalf of someone
            if (request.getCustomerId() == null) {
                return ResponseEntity.badRequest().body(new MessageResponse("Error: customerId is required for offline bookings."));
            }
            customerIdToUse = request.getCustomerId();
            // Owners and staff may only create offline bookings at their own business.
            Business ownBusiness = tenancyService.findBusinessForUser(user).orElse(null);
            Branch targetBranch = branchRepository.findById(request.getBranchId()).orElse(null);
            if (ownBusiness == null || targetBranch == null || targetBranch.getBusiness() == null
                    || !ownBusiness.getId().equals(targetBranch.getBusiness().getId())) {
                return ResponseEntity.status(403).body(new MessageResponse(
                        "You can only create bookings for your own business."));
            }
        }

        try {
            LocalDateTime bookingTime = LocalDateTime.parse(request.getBookingTime(), DateTimeFormatter.ISO_DATE_TIME);
            Booking created = bookingService.createBooking(
                    customerIdToUse,
                    request.getBranchId(),
                    request.getServiceId(),
                    request.getStaffId(),
                    bookingTime,
                    request.getClientNotes(),
                    request.getCustomerPackageId(),
                    request.getPaymentMethod(),
                    new BookingService.VisitRequest(request.getAddressId(), request.isUrgent(),
                            userDetails.getRole() == UserRole.CUSTOMER, request.getPromoCode())
            );

            Booking booking = bookingRepository.findByIdWithDetails(created.getId()).orElse(created);

            User customerUser = userRepository.findById(customerIdToUse).orElse(null);
            boolean awaitingOnline = booking.getStatus() == BookingStatus.PENDING
                    && "ONLINE".equalsIgnoreCase(booking.getPaymentMethod());
            notificationService.notify(
                    customerUser,
                    awaitingOnline ? "Booking pending payment" : "Booking confirmed",
                    awaitingOnline
                            ? "Complete payment to confirm your appointment for " + booking.getBookingTime() + "."
                            : "Your appointment is confirmed for " + booking.getBookingTime() + "."
            );
            if (customerUser != null && booking.getService() != null && booking.getBranch() != null) {
                mailService.sendBookingCreatedEmail(
                        customerUser.getEmail(),
                        customerUser.getFirstName(),
                        booking.getService().getName(),
                        booking.getBookingTime().toString(),
                        booking.getBranch().getName()
                );
            }

            if (booking.resolvedBusiness() != null) {
                tenancyService.findOwner(booking.resolvedBusiness())
                        .ifPresent(owner -> notificationService.notify(
                                owner,
                                "New booking",
                                "A new booking was created for " + bookingTime + "."
                        ));
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
        if (userDetails.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = tenancyService.findBusinessForUser(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (!tenancyService.branchBelongsToBusiness(branch, business)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access."));
            }
        } else if (userDetails.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized branch access for staff."));
            }
        }

        List<Booking> bookings = bookingRepository.findByBranchWithDetails(branch);
        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getBookingDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        User user = userRepository.findById(userDetails.getId()).orElseThrow();

        // Check Authorization
        boolean isAuthorized = false;
        if (userDetails.getRole() == UserRole.SUPER_ADMIN) {
            isAuthorized = true;
        } else if (userDetails.getRole() == UserRole.CUSTOMER && booking.getCustomer().getId().equals(user.getId())) {
            isAuthorized = true;
        } else if (userDetails.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = tenancyService.findBusinessForUser(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (tenancyService.bookingBelongsToBusiness(booking, business)) {
                isAuthorized = true;
            }
        } else if (userDetails.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (booking.getBranch() != null && staff.getBranch() != null
                    && booking.getBranch().getId().equals(staff.getBranch().getId())) {
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

        Booking booking = bookingRepository.findByIdWithDetails(id)
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
        if (userDetails.getRole() == UserRole.BUSINESS_OWNER) {
            Business business = tenancyService.findBusinessForUser(user)
                    .orElseThrow(() -> new RuntimeException("Business not found."));
            if (tenancyService.bookingBelongsToBusiness(booking, business)) {
                isAuthorized = true;
            }
        } else if (userDetails.getRole() == UserRole.BUSINESS_STAFF) {
            Staff staff = staffRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Staff account not found."));
            if (booking.getBranch() != null && staff.getBranch() != null
                    && booking.getBranch().getId().equals(staff.getBranch().getId())) {
                isAuthorized = true;
            }
        } else if (userDetails.getRole() == UserRole.CUSTOMER && booking.getCustomer().getId().equals(user.getId())) {
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

        if (!bookingStatusRules.isValidTransition(booking.getStatus(), newStatus, userDetails.getRole())) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Error: Invalid status transition from " + booking.getStatus() + " to " + newStatus));
        }

        if (newStatus == BookingStatus.CANCELLED && booking.getStatus() != BookingStatus.CANCELLED) {
            if (userDetails.getRole() == UserRole.CUSTOMER) {
                bookingPolicyService.cancelByCustomer(booking, user, null);
            } else {
                bookingPolicyService.cancelByProvider(booking, user, null);
            }
        } else if (newStatus == BookingStatus.NO_SHOW) {
            bookingPolicyService.markNoShow(booking, user);
        } else {
            booking.setStatus(newStatus);
            bookingRepository.save(booking);
        }
        User customerUser = booking.getCustomer() != null
                ? userRepository.findById(booking.getCustomer().getId()).orElse(null)
                : null;
        notificationService.notify(
                customerUser,
                "Booking updated",
                "Your booking status is now " + newStatus + "."
        );
        if (customerUser != null) {
            mailService.sendBookingStatusEmail(
                    customerUser.getEmail(),
                    customerUser.getFirstName(),
                    booking.getService().getName(),
                    booking.getBookingTime().toString(),
                    newStatus.name()
            );
        }

        return ResponseEntity.ok(new MessageResponse("Booking status updated to " + newStatus));
    }

    @GetMapping("/customer/bookings")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getCustomerBookings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User customer = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));
        List<Booking> bookings = bookingRepository.findByCustomerUserWithDetails(customer);
        return ResponseEntity.ok(bookings);
    }

    @PutMapping("/customer/bookings/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> cancelBookingByCustomer(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Booking booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        if (!booking.getCustomer().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized booking cancellation."));
        }

        if (!bookingStatusRules.isValidTransition(booking.getStatus(), BookingStatus.CANCELLED, UserRole.CUSTOMER)) {
            return ResponseEntity.badRequest().body(new MessageResponse(
                    "Error: Cannot cancel a booking in status " + booking.getStatus()));
        }

        com.duemet.booking.services.BookingPolicyService.CancelResult result = null;
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            User customerUser = userRepository.findById(userDetails.getId()).orElse(null);
            result = bookingPolicyService.cancelByCustomer(booking, customerUser, null);
            String serviceName = booking.getService() != null ? booking.getService().getName() : "your appointment";
            String branchName = booking.getBranch() != null ? booking.getBranch().getName() : "the venue";
            notificationService.notify(
                    customerUser,
                    "Booking cancelled",
                    "Your booking for " + serviceName + " has been cancelled."
            );
            if (customerUser != null) {
                mailService.sendBookingCancelledEmail(
                        customerUser.getEmail(),
                        customerUser.getFirstName(),
                        serviceName,
                        String.valueOf(booking.getBookingTime()),
                        branchName
                );
            }
        }

        if (result != null && result.late()) {
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("message", result.fee().signum() > 0
                    ? "Booking cancelled. This was inside the free-cancellation window, so a late-cancellation fee of "
                        + result.currency() + " " + result.fee() + " is due to the provider."
                    : "Booking cancelled (late cancellation).");
            body.put("late", true);
            body.put("fee", result.fee());
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(new MessageResponse("Booking cancelled successfully."));
    }

    /** What cancelling now would mean under the business's policy, so the customer can decide first. */
    @GetMapping("/customer/bookings/{id}/cancel-preview")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> cancelPreview(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Booking booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));
        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized."));
        }
        var preview = bookingPolicyService.previewCustomerCancel(booking);
        var policy = bookingPolicyService.policyFor(booking.resolvedBusiness().getId());
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("late", preview.late());
        body.put("fee", preview.fee());
        body.put("currency", preview.currency());
        body.put("freeCancelHours", policy.freeCancelHours());
        body.put("policyText", policy.policyText());
        return ResponseEntity.ok(body);
    }

    /** Customer's track record, shown to the provider on a booking. */
    @GetMapping("/bookings/{id}/customer-reliability")
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'BUSINESS_STAFF')")
    public ResponseEntity<?> customerReliability(@PathVariable Long id,
                                                 @AuthenticationPrincipal CustomUserDetails userDetails) {
        Booking booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found."));
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        Business own = tenancyService.findBusinessForUser(user).orElse(null);
        if (own == null || !tenancyService.bookingBelongsToBusiness(booking, own) || booking.getCustomer() == null) {
            return ResponseEntity.status(403).body(new MessageResponse("Error: Unauthorized."));
        }
        var r = bookingIncidentRepository.customerReliability(booking.getCustomer().getId());
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("score", r.score());
        body.put("completed", r.completed());
        body.put("noShows", r.noShows());
        body.put("lateCancels", r.lateCancels());
        body.put("cancels", r.cancels());
        bookingIncidentRepository.findByBooking(id).ifPresent(incident -> body.put("incident", incident));
        return ResponseEntity.ok(body);
    }

    @PutMapping("/bookings/{id}/reschedule")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> rescheduleBooking(
            @PathVariable Long id,
            @RequestParam String bookingTime,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        try {
            LocalDateTime newTime = LocalDateTime.parse(bookingTime, DateTimeFormatter.ISO_DATE_TIME);
            Booking updated = bookingService.rescheduleBooking(
                    id,
                    newTime,
                    userDetails.getRole(),
                    userDetails.getId()
            );

            Booking booking = bookingRepository.findByIdWithDetails(updated.getId()).orElse(updated);

            Long customerId = booking.getCustomer() != null ? booking.getCustomer().getId() : null;
            String serviceName = booking.getService() != null ? booking.getService().getName() : "your appointment";
            notificationService.notify(
                    customerId != null ? userRepository.findById(customerId).orElse(null) : null,
                    "Appointment Rescheduled",
                    "Your appointment for " + serviceName + " has been rescheduled to " + newTime + "."
            );

            return ResponseEntity.ok(booking);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponse("Rescheduling failed: " + e.getMessage()));
        }
    }

}
