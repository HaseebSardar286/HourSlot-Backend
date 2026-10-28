package com.duemet.web.controller;

import com.duemet.identity.security.CustomUserDetails;
import com.duemet.identity.repository.UserRepository;
import com.duemet.marketing.BusinessInsightsService;
import com.duemet.marketing.CustomerRecordsService;
import com.duemet.marketing.PromoCodeService;
import com.duemet.marketing.WalkInQueueService;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.TenancyService;
import com.duemet.catalog.repository.ServiceRepository;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Reports, customer records, promo codes and the walk-in queue for the signed-in provider. */
@RestController
public class GrowthToolsController {

    private final BusinessInsightsService insightsService;
    private final CustomerRecordsService customerRecordsService;
    private final PromoCodeService promoCodeService;
    private final WalkInQueueService queueService;
    private final TenancyService tenancyService;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;

    public GrowthToolsController(BusinessInsightsService insightsService,
                                 CustomerRecordsService customerRecordsService,
                                 PromoCodeService promoCodeService,
                                 WalkInQueueService queueService,
                                 TenancyService tenancyService,
                                 UserRepository userRepository,
                                 BusinessRepository businessRepository,
                                 ServiceRepository serviceRepository) {
        this.insightsService = insightsService;
        this.customerRecordsService = customerRecordsService;
        this.promoCodeService = promoCodeService;
        this.queueService = queueService;
        this.tenancyService = tenancyService;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
    }

    // ---- Reports -------------------------------------------------------------------------------------

    @GetMapping("/api/provider-tools/insights")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> insights(@RequestParam(defaultValue = "30") int days,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!List.of(7, 30, 90, 365).contains(days)) {
            throw new IllegalArgumentException("Choose 7, 30, 90 or 365 days.");
        }
        return ResponseEntity.ok(insightsService.report(ownedBusiness(userDetails).getId(), days));
    }

    // ---- Customer records -------------------------------------------------------------------------------

    @GetMapping("/api/provider-tools/customers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> customers(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(customerRecordsService.list(workBusiness(userDetails).getId()));
    }

    @GetMapping("/api/provider-tools/customers/{customerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> customer(@PathVariable Long customerId,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(customerRecordsService.detail(workBusiness(userDetails).getId(), customerId));
    }

    @Data
    public static class NoteBody {
        private String note;
    }

    @PostMapping("/api/provider-tools/customers/{customerId}/notes")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addNote(@PathVariable Long customerId, @RequestBody NoteBody body,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        Business business = workBusiness(userDetails);
        customerRecordsService.addNote(business.getId(), customerId, userDetails.getId(), body.getNote());
        return ResponseEntity.ok(customerRecordsService.detail(business.getId(), customerId));
    }

    @DeleteMapping("/api/provider-tools/customers/{customerId}/notes/{noteId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> deleteNote(@PathVariable Long customerId, @PathVariable Long noteId,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        customerRecordsService.deleteNote(workBusiness(userDetails).getId(), noteId);
        return ResponseEntity.noContent().build();
    }

    @Data
    public static class TagsBody {
        private List<String> tags;
    }

    @PutMapping("/api/provider-tools/customers/{customerId}/tags")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> setTags(@PathVariable Long customerId, @RequestBody TagsBody body,
                                     @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(Map.of("tags",
                customerRecordsService.setTags(workBusiness(userDetails).getId(), customerId, body.getTags())));
    }

    // ---- Promo codes ---------------------------------------------------------------------------------

    @GetMapping("/api/provider-tools/promos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> promos(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(promoCodeService.list(ownedBusiness(userDetails).getId()));
    }

    @Data
    public static class PromoBody {
        private String code;
        private String discountType;
        private BigDecimal discountValue;
        private BigDecimal minAmount;
        /** yyyy-MM-ddTHH:mm[:ss] */
        private String validFrom;
        private String validUntil;
        private Integer maxUses;
        private Integer perCustomerLimit;
    }

    @PostMapping("/api/provider-tools/promos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPromo(@RequestBody PromoBody body,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(promoCodeService.create(ownedBusiness(userDetails).getId(), body.getCode(),
                body.getDiscountType(), body.getDiscountValue(), body.getMinAmount(), parse(body.getValidFrom()),
                parse(body.getValidUntil()), body.getMaxUses(), body.getPerCustomerLimit()));
    }

    @PutMapping("/api/provider-tools/promos/{id}/{state}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> togglePromo(@PathVariable Long id, @PathVariable String state,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        boolean active = switch (state) {
            case "activate" -> true;
            case "deactivate" -> false;
            default -> throw new IllegalArgumentException("Use activate or deactivate.");
        };
        promoCodeService.setActive(ownedBusiness(userDetails).getId(), id, active);
        return ResponseEntity.ok(Map.of("active", active));
    }

    /** Customer checks a code before confirming; price comes from the service, never from the client. */
    @GetMapping("/api/customer/promo-check")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> promoCheck(@RequestParam Long businessId, @RequestParam Long serviceId,
                                        @RequestParam String code,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        var service = serviceRepository.findById(serviceId)
                .filter(s -> s.getBusiness() != null && businessId.equals(s.getBusiness().getId()))
                .orElseThrow(() -> new IllegalArgumentException("Service not found for this provider."));
        BigDecimal price = service.getBasePrice() == null ? BigDecimal.ZERO : service.getBasePrice();
        var applied = promoCodeService.preview(businessId, userDetails.getId(), code, price);
        return ResponseEntity.ok(Map.of("code", applied.code(), "discount", applied.discount(),
                "price", price, "finalPrice", applied.finalPrice()));
    }

    // ---- Walk-in queue -------------------------------------------------------------------------------

    @Data
    public static class QueueBody {
        private String name;
        private String phone;
        private Long serviceId;
    }

    @GetMapping("/api/provider-tools/queue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> queue(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(queueService.today(workBusiness(userDetails).getId()));
    }

    @PostMapping("/api/provider-tools/queue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addToQueue(@RequestBody QueueBody body,
                                        @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(queueService.add(workBusiness(userDetails).getId(), body.getName(),
                body.getPhone(), body.getServiceId()));
    }

    @PostMapping("/api/provider-tools/queue/next")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> callNext(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(queueService.callNext(workBusiness(userDetails).getId()));
    }

    @PutMapping("/api/provider-tools/queue/{id}/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> queueStatus(@PathVariable Long id, @PathVariable String status,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        queueService.setStatus(workBusiness(userDetails).getId(), id, status);
        return ResponseEntity.ok(Map.of("status", status.toUpperCase()));
    }

    /** Public "now serving" screen, e.g. on a TV in the shop or opened from a QR code. */
    @GetMapping("/api/public/queue/{slug}")
    public ResponseEntity<?> publicQueue(@PathVariable String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Business not found."));
        return ResponseEntity.ok(queueService.publicBoard(business.getId(), business.getName()));
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        return LocalDateTime.parse(v.length() == 16 ? v + ":00" : v);
    }

    /** Business the user works at (owner or staff) — for day-to-day tools. */
    private Business workBusiness(CustomUserDetails userDetails) {
        return tenancyService.findBusinessForUser(userRepository.findById(userDetails.getId()).orElseThrow())
                .orElseThrow(() -> new SecurityException("No business is linked to this account."));
    }

    /** Business the user owns — for money and marketing settings. */
    private Business ownedBusiness(CustomUserDetails userDetails) {
        return businessRepository.findFirstByMemberUserId(userDetails.getId())
                .orElseThrow(() -> new SecurityException("Only the business owner can see this."));
    }
}
