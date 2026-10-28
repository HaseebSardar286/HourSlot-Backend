package com.duemet.web.controller;

import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.organization.model.Business;
import com.duemet.organization.services.ProviderOpsService;
import com.duemet.organization.services.TenancyService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
public class ProviderOpsController {
    private final ProviderOpsService providerOpsService;
    private final TenancyService tenancyService;
    private final UserRepository userRepository;
    private final com.duemet.organization.repository.BusinessRepository businessRepository;

    /** Longest single "available now" window, so a forgotten toggle does not keep taking urgent jobs. */
    private static final int MAX_AVAILABLE_NOW_MINUTES = 12 * 60;

    public ProviderOpsController(
            ProviderOpsService providerOpsService,
            TenancyService tenancyService,
            UserRepository userRepository,
            com.duemet.organization.repository.BusinessRepository businessRepository) {
        this.providerOpsService = providerOpsService;
        this.tenancyService = tenancyService;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
    }

    @Data
    public static class AvailableNowBody {
        /** How long to take urgent jobs for; 0 turns it off. */
        private int minutes;
    }

    @PutMapping("/api/provider/ops/available-now")
    public ResponseEntity<?> availableNow(
            @RequestBody AvailableNowBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = requireIndividual(userDetails);
        int minutes = body == null ? 0 : body.getMinutes();
        if (minutes < 0 || minutes > MAX_AVAILABLE_NOW_MINUTES) {
            throw new IllegalArgumentException("Choose between 0 and " + MAX_AVAILABLE_NOW_MINUTES + " minutes.");
        }
        LocalDateTime until = minutes == 0 ? null : LocalDateTime.now().plusMinutes(minutes);
        businessRepository.setAvailableNowUntil(business.getId(), until);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("availableNow", until != null);
        result.put("availableNowUntil", until);
        return ResponseEntity.ok(result);
    }

    @Data
    public static class OpsStatusBody {
        private String opsStatus;
    }

    @GetMapping("/api/provider/ops/today")
    public ResponseEntity<?> today(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.today(requireIndividual(userDetails)));
    }

    @GetMapping("/api/provider/ops/calendar")
    public ResponseEntity<?> calendar(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        LocalDate startDate = from == null || from.isBlank() ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(from);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = to == null || to.isBlank()
                ? startDate.plusMonths(1).atStartOfDay()
                : LocalDate.parse(to).plusDays(1).atStartOfDay();
        return ResponseEntity.ok(providerOpsService.calendar(requireIndividual(userDetails), start, end));
    }

    @GetMapping("/api/provider/ops/analytics")
    public ResponseEntity<?> analytics(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.analytics(requireIndividual(userDetails)));
    }

    @GetMapping("/api/provider/ops/quotes")
    public ResponseEntity<?> quotes(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.quotes(requireIndividual(userDetails)));
    }

    @GetMapping("/api/provider/ops/payments")
    public ResponseEntity<?> payments(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.payments(requireIndividual(userDetails)));
    }

    @PatchMapping("/api/provider/ops/status")
    public ResponseEntity<?> status(
            @RequestBody OpsStatusBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.updateOpsStatus(requireIndividual(userDetails), body.getOpsStatus()));
    }

    @GetMapping("/api/provider/ops/customers")
    public ResponseEntity<?> customers(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.customers(requireIndividual(userDetails)));
    }

    @GetMapping("/api/provider/ops/bookings")
    public ResponseEntity<?> bookings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.bookings(requireIndividual(userDetails)));
    }

    private Business requireIndividual(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return tenancyService.requireIndividualProvider(user);
    }
}
