package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.Organization;
import com.hourslot.model.User;
import com.hourslot.repository.OrganizationRepository;
import com.hourslot.repository.UserRepository;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.TenancyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/business/organization")
@PreAuthorize("hasRole('BUSINESS_OWNER')")
public class OrganizationController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final TenancyService tenancyService;

    public OrganizationController(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            TenancyService tenancyService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.tenancyService = tenancyService;
    }

    @GetMapping
    public ResponseEntity<?> get(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Organization organization = orgFor(userDetails);
        return ResponseEntity.ok(toView(organization));
    }

    @PutMapping
    public ResponseEntity<?> update(
            @Valid @RequestBody OrganizationUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Organization organization = orgFor(userDetails);
        organization.setName(request.getName().trim());
        if (request.getBillingEmail() != null && !request.getBillingEmail().isBlank()) {
            organization.setBillingEmail(request.getBillingEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getDefaultCurrency() != null && !request.getDefaultCurrency().isBlank()) {
            organization.setDefaultCurrency(request.getDefaultCurrency().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getCountryCode() != null) {
            organization.setCountryCode(request.getCountryCode().trim().isEmpty()
                    ? null
                    : request.getCountryCode().trim().toUpperCase(Locale.ROOT));
        }
        if (request.getTimezone() != null) {
            organization.setTimezone(request.getTimezone().trim().isEmpty() ? null : request.getTimezone().trim());
        }
        organizationRepository.save(organization);
        return ResponseEntity.ok(toView(organization));
    }

    private Organization orgFor(CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getId()).orElseThrow();
        return tenancyService.requireOrganizationForUser(user);
    }

    private Map<String, Object> toView(Organization organization) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", organization.getId());
        view.put("name", organization.getName());
        view.put("slug", organization.getSlug());
        view.put("billingEmail", organization.getBillingEmail());
        view.put("status", organization.getStatus());
        view.put("defaultCurrency", organization.getDefaultCurrency());
        view.put("countryCode", organization.getCountryCode());
        view.put("timezone", organization.getTimezone());
        view.put("createdAt", organization.getCreatedAt());
        view.put("updatedAt", organization.getUpdatedAt());
        return view;
    }

    @Data
    public static class OrganizationUpdateRequest {
        @NotBlank
        private String name;
        private String billingEmail;
        private String defaultCurrency;
        private String countryCode;
        private String timezone;
    }
}
