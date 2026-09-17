package com.hourslot.web.controller;

import com.hourslot.booking.model.Booking;
import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.services.TenancyService;
import com.hourslot.quote.model.Quote;
import com.hourslot.quote.services.QuoteService;
import com.hourslot.request.model.ServiceRequest;
import com.hourslot.request.services.RequestMatchingService;
import com.hourslot.request.services.ServiceRequestService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ServiceRequestController {
    private final ServiceRequestService serviceRequestService;
    private final RequestMatchingService matchingService;
    private final QuoteService quoteService;
    private final UserRepository userRepository;
    private final TenancyService tenancyService;

    public ServiceRequestController(
            ServiceRequestService serviceRequestService,
            RequestMatchingService matchingService,
            QuoteService quoteService,
            UserRepository userRepository,
            TenancyService tenancyService) {
        this.serviceRequestService = serviceRequestService;
        this.matchingService = matchingService;
        this.quoteService = quoteService;
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
    }

    @Data
    public static class CreateRequestBody {
        private Long categoryId;
        private Long serviceId;
        private String title;
        private String description;
        private Long customerAddressId;
        private String countryCode;
        private String region;
        private String city;
        private String areaName;
        private Double latitude;
        private Double longitude;
        private LocalDate preferredDate;
        private LocalTime preferredTimeFrom;
        private LocalTime preferredTimeTo;
        private String urgency;
        private BigDecimal budgetMin;
        private BigDecimal budgetMax;
        private String currency;
        private List<String> mediaUrls;
    }

    @Data
    public static class QuoteBody {
        private BigDecimal amount;
        private String currency;
        private String description;
        private Integer estimatedDurationMinutes;
    }

    @Data
    public static class AcceptQuoteBody {
        private Long quoteId;
        private LocalDateTime bookingTime;
    }

    @PostMapping("/api/requests")
    public ResponseEntity<?> create(
            @RequestBody CreateRequestBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = user(userDetails);
        ServiceRequest created = serviceRequestService.create(user, ServiceRequest.builder()
                .categoryId(body.getCategoryId())
                .serviceId(body.getServiceId())
                .title(body.getTitle())
                .description(body.getDescription())
                .customerAddressId(body.getCustomerAddressId())
                .countryCode(body.getCountryCode())
                .region(body.getRegion())
                .city(body.getCity())
                .areaName(body.getAreaName())
                .latitude(body.getLatitude())
                .longitude(body.getLongitude())
                .preferredDate(body.getPreferredDate())
                .preferredTimeFrom(body.getPreferredTimeFrom())
                .preferredTimeTo(body.getPreferredTimeTo())
                .urgency(body.getUrgency() == null ? "NORMAL" : body.getUrgency())
                .budgetMin(body.getBudgetMin())
                .budgetMax(body.getBudgetMax())
                .currency(body.getCurrency() == null ? "PKR" : body.getCurrency())
                .build(), body.getMediaUrls());
        return ResponseEntity.ok(detail(user, created));
    }

    @GetMapping("/api/requests")
    public ResponseEntity<?> listMine(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceRequestService.listForCustomer(user(userDetails)));
    }

    @GetMapping("/api/requests/{id}")
    public ResponseEntity<?> get(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = user(userDetails);
        return ResponseEntity.ok(detail(user, serviceRequestService.requireOwned(user, id)));
    }

    @PostMapping("/api/requests/{id}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(serviceRequestService.cancel(user(userDetails), id));
    }

    @PostMapping("/api/requests/{id}/match")
    public ResponseEntity<?> rematch(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ServiceRequest request = serviceRequestService.requireOwned(user(userDetails), id);
        return ResponseEntity.ok(matchingService.match(request));
    }

    @GetMapping("/api/requests/{id}/providers")
    public ResponseEntity<?> providers(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        serviceRequestService.requireOwned(user(userDetails), id);
        return ResponseEntity.ok(matchingService.providersForRequest(id));
    }

    @GetMapping("/api/requests/{id}/quotes")
    public ResponseEntity<?> quotes(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        serviceRequestService.requireOwned(user(userDetails), id);
        return ResponseEntity.ok(quoteService.listForRequest(id));
    }

    @PostMapping("/api/requests/{id}/select-quote")
    public ResponseEntity<?> selectQuote(
            @PathVariable Long id,
            @RequestBody AcceptQuoteBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Booking booking = quoteService.accept(user(userDetails), id, body.getQuoteId(), body.getBookingTime());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("booking", booking);
        result.put("requestId", id);
        result.put("quoteId", body.getQuoteId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/provider/requests")
    public ResponseEntity<?> providerInbox(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(matchingService.inboxForProviderMarkViewed(requireProvider(userDetails)));
    }

    @PostMapping("/api/provider/requests/{id}/accept")
    public ResponseEntity<?> acceptInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(matchingService.respond(requireProvider(userDetails), id, "ACCEPTED"));
    }

    @PostMapping("/api/provider/requests/{id}/decline")
    public ResponseEntity<?> declineInvite(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(matchingService.respond(requireProvider(userDetails), id, "DECLINED"));
    }

    @PostMapping("/api/provider/requests/{id}/quote")
    public ResponseEntity<?> quote(
            @PathVariable Long id,
            @RequestBody QuoteBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Quote quote = quoteService.submit(
                requireProvider(userDetails),
                id,
                body.getAmount(),
                body.getCurrency(),
                body.getDescription(),
                body.getEstimatedDurationMinutes());
        return ResponseEntity.ok(quote);
    }

    private Map<String, Object> detail(User user, ServiceRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        var providers = matchingService.providersForRequest(request.getId());
        map.put("request", request);
        map.put("media", serviceRequestService.media(request.getId()));
        map.put("providers", providers);
        map.put("quotes", quoteService.listForRequest(request.getId()));
        boolean expanded = providers.stream().anyMatch(row -> {
            Object reason = row.get("matchReason");
            return reason != null && reason.toString().contains("expanded_search");
        });
        map.put("expandedSearch", expanded);
        map.put("matchedCount", providers.size());
        return map;
    }

    private User user(CustomUserDetails details) {
        return userRepository.findById(details.getId()).orElseThrow();
    }

    private Business requireProvider(CustomUserDetails details) {
        return tenancyService.requireBusinessForUser(user(details));
    }
}
