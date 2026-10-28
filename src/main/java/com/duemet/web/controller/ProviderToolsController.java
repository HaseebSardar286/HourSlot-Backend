package com.duemet.web.controller;

import com.duemet.booking.repository.BookingIncidentRepository;
import com.duemet.booking.repository.BusinessPolicyRepository;
import com.duemet.booking.repository.BusinessPolicyRepository.BusinessPolicy;
import com.duemet.booking.services.CalendarFeedService;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.TenancyService;
import com.duemet.identity.repository.UserRepository;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Booking policy, owed fees and calendar sync for the signed-in provider; plus their public views. */
@RestController
public class ProviderToolsController {

    private final BusinessPolicyRepository policyRepository;
    private final BookingIncidentRepository incidentRepository;
    private final CalendarFeedService calendarFeedService;
    private final BusinessRepository businessRepository;
    private final TenancyService tenancyService;
    private final UserRepository userRepository;
    private final String backendBaseUrl;

    public ProviderToolsController(BusinessPolicyRepository policyRepository,
                                   BookingIncidentRepository incidentRepository,
                                   CalendarFeedService calendarFeedService,
                                   BusinessRepository businessRepository,
                                   TenancyService tenancyService,
                                   UserRepository userRepository,
                                   @Value("${app.public-api-base-url:${app.frontend-base-url:http://localhost:3000}}")
                                   String backendBaseUrl) {
        this.policyRepository = policyRepository;
        this.incidentRepository = incidentRepository;
        this.calendarFeedService = calendarFeedService;
        this.businessRepository = businessRepository;
        this.tenancyService = tenancyService;
        this.userRepository = userRepository;
        this.backendBaseUrl = backendBaseUrl.endsWith("/")
                ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1) : backendBaseUrl;
    }

    @Data
    public static class PolicyBody {
        private Integer freeCancelHours;
        private BigDecimal lateCancelFeePercent;
        private BigDecimal noShowFeePercent;
        private Integer prepayAfterNoShows;
        private String policyText;
    }

    @GetMapping("/api/provider-tools/policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getPolicy(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(policyRepository.find(ownBusiness(userDetails).getId()));
    }

    @PutMapping("/api/provider-tools/policy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> savePolicy(@RequestBody PolicyBody body,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = ownedBusiness(userDetails);
        int hours = body.getFreeCancelHours() == null ? 0 : body.getFreeCancelHours();
        int prepay = body.getPrepayAfterNoShows() == null ? 0 : body.getPrepayAfterNoShows();
        BigDecimal late = percent(body.getLateCancelFeePercent());
        BigDecimal noShow = percent(body.getNoShowFeePercent());
        if (hours < 0 || hours > 168) {
            throw new IllegalArgumentException("Free cancellation window must be between 0 and 168 hours.");
        }
        if (prepay < 0 || prepay > 10) {
            throw new IllegalArgumentException("Prepayment rule must be between 0 (off) and 10 no-shows.");
        }
        String text = body.getPolicyText() == null || body.getPolicyText().isBlank() ? null : body.getPolicyText().trim();
        if (text != null && text.length() > 2000) {
            throw new IllegalArgumentException("Policy text is limited to 2000 characters.");
        }
        return ResponseEntity.ok(policyRepository.save(
                new BusinessPolicy(business.getId(), hours, late, noShow, prepay, text)));
    }

    /** Late-cancellation and no-show fees customers owe this business. */
    @GetMapping("/api/provider-tools/fees")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> owedFees(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(incidentRepository.findOwedByBusiness(ownBusiness(userDetails).getId()));
    }

    @PostMapping("/api/provider-tools/fees/{bookingId}/{action}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> settleFee(@PathVariable Long bookingId, @PathVariable String action,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        String status = switch (action.toLowerCase()) {
            case "collected" -> "COLLECTED";
            case "waived" -> "WAIVED";
            default -> throw new IllegalArgumentException("Use 'collected' or 'waived'.");
        };
        if (incidentRepository.setFeeStatus(bookingId, ownBusiness(userDetails).getId(), status) == 0) {
            throw new RuntimeException("No open fee found for this booking.");
        }
        return ResponseEntity.ok(Map.of("feeStatus", status));
    }

    @GetMapping("/api/provider-tools/calendar-feed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> calendarFeed(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(feedView(calendarFeedService.tokenFor(ownedBusiness(userDetails).getId())));
    }

    @PostMapping("/api/provider-tools/calendar-feed/rotate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> rotateCalendarFeed(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(feedView(calendarFeedService.rotate(ownedBusiness(userDetails).getId())));
    }

    /** Subscribed to by calendar apps; the unguessable token is the only credential. */
    @GetMapping(value = "/api/public/calendar/{token}.ics")
    public ResponseEntity<String> calendarIcs(@PathVariable String token) {
        return calendarFeedService.render(token)
                .map(ics -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                        .header("Cache-Control", "no-store")
                        .body(ics))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Policy and reliability shown to customers before they book. */
    @GetMapping("/api/public/businesses/{id}/booking-terms")
    public ResponseEntity<?> publicTerms(@PathVariable Long id) {
        BusinessPolicy policy = policyRepository.find(id);
        var reliability = incidentRepository.providerReliability(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("freeCancelHours", policy.freeCancelHours());
        body.put("lateCancelFeePercent", policy.lateCancelFeePercent());
        body.put("noShowFeePercent", policy.noShowFeePercent());
        body.put("policyText", policy.policyText());
        // Only claim a score once there is enough history for it to mean something.
        body.put("reliabilityScore", reliability.completed() + reliability.lateCancels() >= 5 ? reliability.score() : null);
        body.put("completedBookings", reliability.completed());
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> feedView(String token) {
        String url = backendBaseUrl + "/api/public/calendar/" + token + ".ics";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("webcalUrl", url.replaceFirst("^https?://", "webcal://"));
        return body;
    }

    private static BigDecimal percent(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        if (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Fees are a percentage between 0 and 100.");
        }
        return v;
    }

    /** Business the user works at (owner, member or staff). */
    private Business ownBusiness(CustomUserDetails userDetails) {
        return tenancyService.findBusinessForUser(userRepository.findById(userDetails.getId()).orElseThrow())
                .orElseThrow(() -> new SecurityException("No business is linked to this account."));
    }

    /** Business the user owns or manages; staff members cannot change settings. */
    private Business ownedBusiness(CustomUserDetails userDetails) {
        return businessRepository.findFirstByMemberUserId(userDetails.getId())
                .orElseThrow(() -> new SecurityException("Only the business owner can change this."));
    }
}
