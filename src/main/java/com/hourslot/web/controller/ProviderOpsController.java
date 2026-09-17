package com.hourslot.web.controller;

import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.identity.security.CustomUserDetails;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.services.ProviderOpsService;
import com.hourslot.organization.services.TenancyService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    public ProviderOpsController(
            ProviderOpsService providerOpsService,
            TenancyService tenancyService,
            UserRepository userRepository) {
        this.providerOpsService = providerOpsService;
        this.tenancyService = tenancyService;
        this.userRepository = userRepository;
    }

    @Data
    public static class OpsStatusBody {
        private String opsStatus;
    }

    @GetMapping("/api/provider/ops/today")
    public ResponseEntity<?> today(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.today(requireBusiness(userDetails)));
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
        return ResponseEntity.ok(providerOpsService.calendar(requireBusiness(userDetails), start, end));
    }

    @GetMapping("/api/provider/ops/analytics")
    public ResponseEntity<?> analytics(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.analytics(requireBusiness(userDetails)));
    }

    @GetMapping("/api/provider/ops/quotes")
    public ResponseEntity<?> quotes(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.quotes(requireBusiness(userDetails)));
    }

    @GetMapping("/api/provider/ops/payments")
    public ResponseEntity<?> payments(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.payments(requireBusiness(userDetails)));
    }

    @PatchMapping("/api/provider/ops/status")
    public ResponseEntity<?> status(
            @RequestBody OpsStatusBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.updateOpsStatus(requireBusiness(userDetails), body.getOpsStatus()));
    }

    @GetMapping("/api/provider/ops/customers")
    public ResponseEntity<?> customers(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(providerOpsService.customers(requireBusiness(userDetails)));
    }

    private Business requireBusiness(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return tenancyService.requireBusinessForUser(user);
    }
}
