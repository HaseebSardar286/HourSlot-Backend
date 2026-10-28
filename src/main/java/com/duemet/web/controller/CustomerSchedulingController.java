package com.duemet.web.controller;

import com.duemet.booking.model.Booking;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingSeriesRepository;
import com.duemet.booking.repository.WaitlistRepository;
import com.duemet.booking.services.BookingPolicyService;
import com.duemet.booking.services.BookingService;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.organization.model.Branch;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.services.EntitlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Customer-side scheduling extras: waitlist for full days and repeat (recurring) bookings. */
@RestController
@RequestMapping("/api/customer")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerSchedulingController {

    static final int MAX_OCCURRENCES = 12;

    private final WaitlistRepository waitlistRepository;
    private final BookingSeriesRepository seriesRepository;
    private final BookingService bookingService;
    private final BookingPolicyService bookingPolicyService;
    private final BookingRepository bookingRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final com.duemet.organization.repository.BusinessRepository businessRepository;
    private final EntitlementService entitlementService;

    public CustomerSchedulingController(WaitlistRepository waitlistRepository,
                                        BookingSeriesRepository seriesRepository,
                                        BookingService bookingService,
                                        BookingPolicyService bookingPolicyService,
                                        BookingRepository bookingRepository,
                                        BranchRepository branchRepository,
                                        UserRepository userRepository,
                                        com.duemet.organization.repository.BusinessRepository businessRepository,
                                        EntitlementService entitlementService) {
        this.waitlistRepository = waitlistRepository;
        this.seriesRepository = seriesRepository;
        this.bookingService = bookingService;
        this.bookingPolicyService = bookingPolicyService;
        this.bookingRepository = bookingRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.entitlementService = entitlementService;
    }

    @Data
    public static class WaitlistBody {
        @NotNull private Long branchId;
        @NotNull private Long serviceId;
        /** yyyy-MM-dd */
        @NotNull private String date;
    }

    @PostMapping("/waitlist")
    public ResponseEntity<?> joinWaitlist(@Valid @RequestBody WaitlistBody body,
                                          @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate date = LocalDate.parse(body.getDate());
        if (date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Choose today or a future date.");
        }
        Branch branch = branchRepository.findById(body.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));
        if (branch.getBusiness() == null) {
            throw new IllegalArgumentException("Branch is not linked to a business.");
        }
        // Waitlist is a plan feature of the provider (off on Starter).
        var business = businessRepository.findById(branch.getBusiness().getId()).orElseThrow();
        if (!entitlementService.allows(business.getOrganization(), EntitlementService.WAITLIST)) {
            throw new IllegalStateException("This provider does not offer a waitlist.");
        }
        boolean added = waitlistRepository.join(userDetails.getId(), branch.getBusiness().getId(),
                branch.getId(), body.getServiceId(), date);
        return ResponseEntity.ok(Map.of("message", added
                ? "You're on the waitlist. We'll email you if a slot opens up that day."
                : "You're already on the waitlist for that day."));
    }

    @GetMapping("/waitlist")
    public ResponseEntity<?> myWaitlist(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(waitlistRepository.findForCustomer(userDetails.getId()));
    }

    @DeleteMapping("/waitlist/{id}")
    public ResponseEntity<?> leaveWaitlist(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (waitlistRepository.cancel(id, userDetails.getId()) == 0) {
            throw new RuntimeException("Waitlist entry not found.");
        }
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class RecurringBody {
        @NotNull private Long branchId;
        @NotNull private Long serviceId;
        private Long staffId;
        /** First appointment, yyyy-MM-ddTHH:mm:ss */
        @NotNull private String bookingTime;
        /** WEEKLY | BIWEEKLY | MONTHLY */
        @NotNull private String frequency;
        @NotNull private Integer occurrences;
        private String clientNotes;
        private Long addressId;
    }

    /**
     * Book the same service repeatedly (e.g. weekly cleaning). Each date is booked on its own, so a
     * clash on one date does not stop the others; skipped dates are returned with the reason.
     * Pay-at-venue only, since each visit is paid when it happens.
     */
    @PostMapping("/bookings/recurring")
    public ResponseEntity<?> bookRecurring(@Valid @RequestBody RecurringBody body,
                                           @AuthenticationPrincipal CustomUserDetails userDetails) {
        String frequency = body.getFrequency().trim().toUpperCase();
        if (!List.of("WEEKLY", "BIWEEKLY", "MONTHLY").contains(frequency)) {
            throw new IllegalArgumentException("Frequency must be WEEKLY, BIWEEKLY or MONTHLY.");
        }
        int count = body.getOccurrences();
        if (count < 2 || count > MAX_OCCURRENCES) {
            throw new IllegalArgumentException("Choose between 2 and " + MAX_OCCURRENCES + " appointments.");
        }
        LocalDateTime first = LocalDateTime.parse(body.getBookingTime(), DateTimeFormatter.ISO_DATE_TIME);
        Branch branch = branchRepository.findById(body.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found."));

        List<Map<String, Object>> booked = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        Long seriesId = null;
        for (LocalDateTime when : occurrences(first, frequency, count)) {
            try {
                // Each call is its own transaction (BookingService is proxied), so one clash rolls back only that date.
                Booking created = bookingService.createBooking(userDetails.getId(), body.getBranchId(),
                        body.getServiceId(), body.getStaffId(), when, body.getClientNotes(), null, "VENUE",
                        new BookingService.VisitRequest(body.getAddressId(), false, true));
                if (seriesId == null) {
                    seriesId = seriesRepository.create(userDetails.getId(), branch.getBusiness().getId(), frequency, count);
                }
                seriesRepository.addBooking(seriesId, created.getId());
                booked.add(Map.of("bookingId", created.getId(), "bookingTime", when.toString()));
            } catch (RuntimeException ex) {
                skipped.add(Map.of("bookingTime", when.toString(),
                        "reason", ex.getMessage() == null ? "Not available" : ex.getMessage()));
            }
        }
        if (booked.isEmpty()) {
            throw new IllegalArgumentException("None of those dates could be booked: "
                    + (skipped.isEmpty() ? "" : skipped.get(0).get("reason")));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("seriesId", seriesId);
        result.put("booked", booked);
        result.put("skipped", skipped);
        return ResponseEntity.ok(result);
    }

    /** Cancel every upcoming appointment in a series (each under the business's cancellation policy). */
    @PostMapping("/booking-series/{id}/cancel")
    public ResponseEntity<?> cancelSeries(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        var series = seriesRepository.find(id).orElseThrow(() -> new RuntimeException("Series not found."));
        if (!series.customerUserId().equals(userDetails.getId())) {
            throw new SecurityException("Not your booking series.");
        }
        User customer = userRepository.findById(userDetails.getId()).orElseThrow();
        int cancelled = 0;
        java.math.BigDecimal fees = java.math.BigDecimal.ZERO;
        for (Long bookingId : seriesRepository.upcomingActiveBookingIds(id)) {
            Booking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
            if (booking == null) {
                continue;
            }
            fees = fees.add(bookingPolicyService.cancelByCustomer(booking, customer, "Series cancelled").fee());
            cancelled++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cancelled", cancelled);
        result.put("lateFees", fees);
        return ResponseEntity.ok(result);
    }

    static List<LocalDateTime> occurrences(LocalDateTime first, String frequency, int count) {
        List<LocalDateTime> dates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            dates.add(switch (frequency) {
                case "WEEKLY" -> first.plusWeeks(i);
                case "BIWEEKLY" -> first.plusWeeks(2L * i);
                default -> first.plusMonths(i);
            });
        }
        return dates;
    }
}
