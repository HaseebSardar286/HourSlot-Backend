package com.duemet.web.controller;

import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.security.CustomUserDetails;
import com.duemet.job.model.Dispute;
import com.duemet.job.model.Job;
import com.duemet.job.repository.DisputeRepository;
import com.duemet.job.repository.JobRepository;
import com.duemet.job.services.JobService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminDisputeController {
    private final DisputeRepository disputeRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final UserRepository userRepository;

    public AdminDisputeController(
            DisputeRepository disputeRepository,
            JobRepository jobRepository,
            JobService jobService,
            UserRepository userRepository) {
        this.disputeRepository = disputeRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
        this.userRepository = userRepository;
    }

    @Data
    public static class ResolveBody {
        private String resolution;
        private String status;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status) {
        List<Dispute> disputes = status == null || status.isBlank()
                ? disputeRepository.findByStatus("OPEN")
                : disputeRepository.findByStatus(status.trim().toUpperCase());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("disputes", disputes);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<?> resolve(
            @PathVariable Long id,
            @RequestBody ResolveBody body,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User admin = userRepository.findById(userDetails.getId()).orElseThrow();
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dispute not found."));
        dispute.setStatus(body.getStatus() == null || body.getStatus().isBlank()
                ? "RESOLVED" : body.getStatus().trim().toUpperCase());
        dispute.setResolutionNotes(body.getResolution());
        disputeRepository.save(dispute);
        if ("RESOLVED".equalsIgnoreCase(dispute.getStatus())) {
            jobRepository.findById(dispute.getJobId()).ifPresent(job -> {
                if ("DISPUTED".equalsIgnoreCase(job.getStatus())) {
                    jobService.transition(job.getId(), "COMPLETED", admin, job.getProviderId(),
                            "Admin resolved dispute", job.getFinalAmount() != null
                                    ? job.getFinalAmount() : job.getEstimatedAmount());
                }
            });
        }
        return ResponseEntity.ok(dispute);
    }
}
