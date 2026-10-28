package com.duemet.web.controller;

import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.job.model.Job;
import com.duemet.job.services.JobService;
import com.duemet.organization.model.Business;
import com.duemet.organization.services.FeatureFlagService;
import com.duemet.organization.services.TenancyService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;
    private final UserRepository userRepository;
    private final TenancyService tenancyService;
    private final FeatureFlagService featureFlagService;
    private final com.duemet.job.services.JobReceiptService jobReceiptService;

    public JobController(
            JobService jobService,
            UserRepository userRepository,
            TenancyService tenancyService,
            FeatureFlagService featureFlagService,
            com.duemet.job.services.JobReceiptService jobReceiptService) {
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.featureFlagService = featureFlagService;
        this.jobReceiptService = jobReceiptService;
    }

    @Data
    public static class TransitionBody {
        private String reason;
        private BigDecimal finalAmount;
    }

    @Data
    public static class NoteBody {
        private String body;
    }

    @Data
    public static class MediaBody {
        private Long mediaAssetId;
        private String url;
        private String storageKey;
        private String mimeType;
        private Integer sortOrder;
    }

    @Data
    public static class DisputeBody {
        private String reason;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String role,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        Long providerBusinessId = resolveProviderBusinessId(userDetails, role);
        List<Job> jobs;
        if ("provider".equalsIgnoreCase(role)) {
            Business business = tenancyService.requireBusinessForUser(user);
            jobs = jobService.listForProvider(business.getId());
        } else {
            jobs = jobService.listForCustomer(user.getId());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jobs", jobs);
        result.put("role", role == null ? "customer" : role);
        result.put("providerBusinessId", providerBusinessId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.detail(user, id, providerBusinessId(user)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<?> accept(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestBody(required = false) TransitionBody body) {
        return transition(id, "ACCEPTED", userDetails, body);
    }

    @PostMapping("/{id}/en-route")
    public ResponseEntity<?> enRoute(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                     @RequestBody(required = false) TransitionBody body) {
        return transition(id, "EN_ROUTE", userDetails, body);
    }

    @PostMapping("/{id}/arrived")
    public ResponseEntity<?> arrived(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                     @RequestBody(required = false) TransitionBody body) {
        return transition(id, "ARRIVED", userDetails, body);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<?> start(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                   @RequestBody(required = false) TransitionBody body) {
        return transition(id, "IN_PROGRESS", userDetails, body);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                      @RequestBody(required = false) TransitionBody body) {
        return transition(id, "COMPLETED", userDetails, body);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails userDetails,
                                    @RequestBody(required = false) TransitionBody body) {
        return transition(id, "CANCELLED", userDetails, body);
    }

    @PostMapping("/{id}/dispute")
    public ResponseEntity<?> dispute(
            @PathVariable Long id,
            @RequestBody DisputeBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.openDispute(id, user, providerBusinessId(user), body.getReason()));
    }

    @PostMapping("/{id}/media")
    public ResponseEntity<?> media(
            @PathVariable Long id,
            @RequestBody MediaBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.addMedia(id, user, providerBusinessId(user),
                body.getMediaAssetId(), body.getUrl(), body.getStorageKey(),
                body.getMimeType(), body.getSortOrder()));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<?> notes(
            @PathVariable Long id,
            @RequestBody NoteBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.addNote(id, user, providerBusinessId(user), body.getBody()));
    }

    @Data
    public static class MaterialBody {
        private String name;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
    }

    @Data
    public static class CollectPaymentBody {
        /** Defaults to the job's final amount. */
        private BigDecimal amount;
        /** CASH | BANK_TRANSFER | WALLET; defaults to CASH. */
        private String method;
    }

    @PostMapping("/{id}/materials")
    public ResponseEntity<?> addMaterial(
            @PathVariable Long id,
            @RequestBody MaterialBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.addMaterial(id, user, providerBusinessId(user),
                body.getName(), body.getQuantity(), body.getUnitPrice()));
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    public ResponseEntity<?> removeMaterial(
            @PathVariable Long id,
            @PathVariable Long materialId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        jobService.removeMaterial(id, materialId, user, providerBusinessId(user));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmCompletion(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        return ResponseEntity.ok(jobService.confirmCompletion(id, user(userDetails)));
    }

    @PostMapping("/{id}/collect-payment")
    public ResponseEntity<?> collectPayment(
            @PathVariable Long id,
            @RequestBody(required = false) CollectPaymentBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        return ResponseEntity.ok(jobService.collectPayment(id, user, providerBusinessId(user),
                body == null ? null : body.getAmount(), body == null ? null : body.getMethod()));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        requireJobsEnabled();
        User user = user(userDetails);
        byte[] pdf = jobReceiptService.render(id, user, providerBusinessId(user));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"receipt-job-" + id + ".pdf\"")
                .header("Cache-Control", "no-store")
                .body(pdf);
    }

    private ResponseEntity<?> transition(Long id, String status, CustomUserDetails userDetails, TransitionBody body) {
        requireJobsEnabled();
        User user = user(userDetails);
        String reason = body == null ? null : body.getReason();
        BigDecimal finalAmount = body == null ? null : body.getFinalAmount();
        return ResponseEntity.ok(jobService.transition(id, status, user, providerBusinessId(user), reason, finalAmount));
    }

    private void requireJobsEnabled() {
        if (!featureFlagService.isEnabled("jobs")) {
            throw new IllegalStateException("Jobs feature is not enabled.");
        }
    }

    private User user(CustomUserDetails details) {
        return userRepository.findById(details.getId()).orElseThrow();
    }

    private Long providerBusinessId(User user) {
        try {
            return tenancyService.requireBusinessForUser(user).getId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long resolveProviderBusinessId(CustomUserDetails details, String role) {
        if (!"provider".equalsIgnoreCase(role)) {
            return null;
        }
        return providerBusinessId(user(details));
    }
}
