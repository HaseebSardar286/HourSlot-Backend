package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.model.*;
import com.hourslot.repository.*;
import com.hourslot.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CustomerPackageController {

    @Autowired
    private CustomerPackageRepository customerPackageRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @GetMapping("/customer/packages")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getCustomerPackages(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Customer customer = customerRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));
        List<CustomerPackage> pkgs = customerPackageRepository.findByCustomerOrderByCreatedAtDesc(customer);
        return ResponseEntity.ok(pkgs);
    }

    @GetMapping("/customer/packages/eligible")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getEligiblePackages(
            @RequestParam Long serviceId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Customer customer = customerRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        com.hourslot.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found."));

        List<CustomerPackage> allActive = customerPackageRepository.findByCustomerAndStatus(customer, "ACTIVE");
        List<CustomerPackage> eligible = new ArrayList<>();

        for (CustomerPackage cp : allActive) {
            // Check expiry
            if (cp.getExpiresAt() != null && LocalDateTime.now().isAfter(cp.getExpiresAt())) {
                cp.setStatus("EXPIRED");
                customerPackageRepository.save(cp);
                continue;
            }
            if (cp.getSessionsRemaining() <= 0) {
                cp.setStatus("EXHAUSTED");
                customerPackageRepository.save(cp);
                continue;
            }

            // Check if service is included in this package
            ServicePackage sp = cp.getServicePackage();
            boolean isIncluded = sp.getServices().stream().anyMatch(s -> s.getId().equals(serviceId));
            if (isIncluded) {
                eligible.add(cp);
            }
        }

        return ResponseEntity.ok(eligible);
    }

    @PostMapping("/packages/{id}/purchase")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> purchasePackage(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "VENUE") String paymentMethod,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Customer customer = customerRepository.findById(userDetails.getId())
                .orElseThrow(() -> new RuntimeException("Customer not found."));

        ServicePackage servicePackage = servicePackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service Package not found."));

        if (paymentMethod.equalsIgnoreCase("ONLINE")) {
            Map<String, Object> res = new HashMap<>();
            res.put("stripeCheckoutRequired", true);
            res.put("packageId", id);
            return ResponseEntity.ok(res);
        }

        LocalDateTime expiresAt = null;
        if (servicePackage.getExpiryDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(servicePackage.getExpiryDays());
        }

        CustomerPackage cp = CustomerPackage.builder()
                .customer(customer)
                .servicePackage(servicePackage)
                .sessionsRemaining(servicePackage.getSessionsCount())
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();

        customerPackageRepository.save(cp);
        return ResponseEntity.ok(cp);
    }
}
