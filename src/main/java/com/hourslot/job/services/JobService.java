package com.hourslot.job.services;

import com.hourslot.booking.model.Booking;
import com.hourslot.booking.model.BookingStatus;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.catalog.model.Service;
import com.hourslot.identity.model.User;
import com.hourslot.job.model.Dispute;
import com.hourslot.job.model.Job;
import com.hourslot.job.model.JobMedia;
import com.hourslot.job.model.JobNote;
import com.hourslot.job.model.JobStatusHistory;
import com.hourslot.job.repository.DisputeRepository;
import com.hourslot.job.repository.JobMediaRepository;
import com.hourslot.job.repository.JobNoteRepository;
import com.hourslot.job.repository.JobRepository;
import com.hourslot.job.repository.JobStatusHistoryRepository;
import com.hourslot.request.repository.ServiceRequestRepository;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.organization.services.AuditService;
import com.hourslot.notification.services.NotificationService;
import com.hourslot.identity.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@org.springframework.stereotype.Service
public class JobService {
    private static final Map<String, Set<String>> ALLOWED = new LinkedHashMap<>();

    static {
        ALLOWED.put("REQUESTED", Set.of("ACCEPTED", "SCHEDULED", "CANCELLED"));
        ALLOWED.put("ACCEPTED", Set.of("SCHEDULED", "EN_ROUTE", "CANCELLED"));
        ALLOWED.put("SCHEDULED", Set.of("ACCEPTED", "EN_ROUTE", "CANCELLED"));
        ALLOWED.put("EN_ROUTE", Set.of("ARRIVED", "CANCELLED"));
        ALLOWED.put("ARRIVED", Set.of("IN_PROGRESS", "CANCELLED"));
        ALLOWED.put("IN_PROGRESS", Set.of("AWAITING_PAYMENT", "COMPLETED", "CANCELLED"));
        ALLOWED.put("AWAITING_PAYMENT", Set.of("COMPLETED", "DISPUTED", "CANCELLED"));
        ALLOWED.put("COMPLETED", Set.of("DISPUTED"));
        ALLOWED.put("DISPUTED", Set.of("COMPLETED"));
        ALLOWED.put("CANCELLED", Set.of());
    }

    private final JobRepository jobRepository;
    private final JobStatusHistoryRepository historyRepository;
    private final JobMediaRepository mediaRepository;
    private final JobNoteRepository noteRepository;
    private final DisputeRepository disputeRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final BookingRepository bookingRepository;
    private final AuditService auditService;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public JobService(
            JobRepository jobRepository,
            JobStatusHistoryRepository historyRepository,
            JobMediaRepository mediaRepository,
            JobNoteRepository noteRepository,
            DisputeRepository disputeRepository,
            ServiceRequestRepository serviceRequestRepository,
            BookingRepository bookingRepository,
            AuditService auditService,
            BusinessRepository businessRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.historyRepository = historyRepository;
        this.mediaRepository = mediaRepository;
        this.noteRepository = noteRepository;
        this.disputeRepository = disputeRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.bookingRepository = bookingRepository;
        this.auditService = auditService;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Job createFromBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking is required.");
        }
        if (!"REQUEST".equalsIgnoreCase(booking.getSource())) {
            return null;
        }
        if (jobRepository.findByBookingId(booking.getId()).isPresent()) {
            return jobRepository.findByBookingId(booking.getId()).orElseThrow();
        }

        Long providerId = booking.resolvedBusiness() != null ? booking.resolvedBusiness().getId() : booking.getBusiness() != null ? booking.getBusiness().getId() : null;
        if (providerId == null && booking.getBranch() != null && booking.getBranch().getBusiness() != null) {
            providerId = booking.getBranch().getBusiness().getId();
        }
        if (providerId == null) {
            throw new IllegalStateException("Cannot resolve provider for job from booking " + booking.getId());
        }

        Long customerId = booking.getCustomerUser() != null ? booking.getCustomerUser().getId() : null;
        if (customerId == null) {
            throw new IllegalStateException("Cannot resolve customer for job from booking " + booking.getId());
        }

        Service service = booking.getService();
        Long serviceId = service != null ? service.getId() : null;
        String title = service != null && service.getName() != null ? service.getName() : "Service job";
        String description = booking.getClientNotes();

        String requestTitle = serviceRequestRepository.findById(booking.getServiceRequestId())
                .map(r -> r.getTitle())
                .orElse(null);
        if (requestTitle != null && !requestTitle.isBlank()) {
            title = requestTitle;
        }

        String initialStatus = booking.getBookingTime() != null ? "SCHEDULED" : "REQUESTED";
        Job job = Job.builder()
                .bookingId(booking.getId())
                .serviceRequestId(booking.getServiceRequestId())
                .providerId(providerId)
                .customerId(customerId)
                .serviceId(serviceId)
                .title(title)
                .description(description)
                .estimatedAmount(booking.getTotalPrice())
                .currency(booking.getCurrency() != null ? booking.getCurrency() : "PKR")
                .status(initialStatus)
                .scheduledStart(booking.getBookingTime())
                .scheduledEnd(booking.getEndTime())
                .build();
        jobRepository.save(job);
        recordHistory(job.getId(), null, job.getStatus(), null, "Created from booking");
        return job;
    }

    @Transactional(readOnly = true)
    public List<Job> listForProvider(Long providerId) {
        return jobRepository.findByProvider(providerId);
    }

    @Transactional(readOnly = true)
    public List<Job> listForCustomer(Long customerId) {
        return jobRepository.findByCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public Job requireAccessible(User actor, Long jobId, Long providerBusinessId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));
        if (!canAccess(actor, job, providerBusinessId)) {
            throw new SecurityException("Not allowed to access this job.");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(User actor, Long jobId, Long providerBusinessId) {
        Job job = requireAccessible(actor, jobId, providerBusinessId);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("job", job);
        map.put("history", historyRepository.findByJob(jobId));
        map.put("media", mediaRepository.findByJob(jobId));
        map.put("notes", noteRepository.findByJob(jobId));
        map.put("disputes", disputeRepository.findByJob(jobId));
        return map;
    }

    @Transactional
    public Job transition(Long jobId, String newStatus, User actor, Long providerBusinessId,
                          String reason, BigDecimal finalAmount) {
        Job job = requireAccessible(actor, jobId, providerBusinessId);
        String target = newStatus == null ? null : newStatus.toUpperCase();
        String current = job.getStatus().toUpperCase();
        Set<String> allowed = ALLOWED.getOrDefault(current, Set.of());
        if (target == null || !allowed.contains(target)) {
            throw new IllegalStateException("Cannot transition from " + current + " to " + target);
        }
        if ("COMPLETED".equals(target) && finalAmount != null) {
            job.setFinalAmount(finalAmount);
        } else if ("COMPLETED".equals(target) && job.getFinalAmount() == null) {
            job.setFinalAmount(job.getEstimatedAmount());
        }
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case "IN_PROGRESS" -> job.setStartedAt(now);
            case "COMPLETED" -> {
                job.setCompletedAt(now);
                completeLinkedBooking(job);
            }
            case "CANCELLED" -> job.setCancelledAt(now);
            default -> { }
        }
        recordHistory(jobId, current, target, actor.getId(), reason);
        job.setStatus(target);
        Job saved = jobRepository.save(job);
        if ("COMPLETED".equals(target) || "CANCELLED".equals(target)) {
            Business provider = businessRepository.findById(saved.getProviderId()).orElse(null);
            auditService.log(actor, provider, "JOB_" + target, "Job", saved.getId(), reason);
            userRepository.findById(saved.getCustomerId()).ifPresent(customer ->
                    notificationService.notify(customer, "JOB_" + target, "Job update",
                            saved.getTitle() + " is now " + target.toLowerCase().replace('_', ' ') + ".",
                            saved.getId()));
        }
        return saved;
    }

    @Transactional
    public JobNote addNote(Long jobId, User actor, Long providerBusinessId, String body) {
        requireAccessible(actor, jobId, providerBusinessId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Note body is required.");
        }
        return noteRepository.save(JobNote.builder()
                .jobId(jobId)
                .authorUserId(actor.getId())
                .body(body.trim())
                .build());
    }

    @Transactional
    public JobMedia addMedia(Long jobId, User actor, Long providerBusinessId, Long mediaAssetId, String url,
                             String storageKey, String mimeType, Integer sortOrder) {
        requireAccessible(actor, jobId, providerBusinessId);
        if ((url == null || url.isBlank()) && mediaAssetId == null) {
            throw new IllegalArgumentException("url or mediaAssetId is required.");
        }
        return mediaRepository.save(JobMedia.builder()
                .jobId(jobId)
                .mediaAssetId(mediaAssetId)
                .url(url)
                .storageKey(storageKey)
                .mimeType(mimeType)
                .sortOrder(sortOrder == null ? 0 : sortOrder)
                .build());
    }

    @Transactional
    public Dispute openDispute(Long jobId, User actor, Long providerBusinessId, String reason) {
        Job job = requireAccessible(actor, jobId, providerBusinessId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Dispute reason is required.");
        }
        Dispute dispute = disputeRepository.save(Dispute.builder()
                .jobId(jobId)
                .openedByUserId(actor.getId())
                .reason(reason.trim())
                .status("OPEN")
                .build());
        if (!"DISPUTED".equalsIgnoreCase(job.getStatus())) {
            recordHistory(jobId, job.getStatus(), "DISPUTED", actor.getId(), reason);
            job.setStatus("DISPUTED");
            jobRepository.save(job);
        }
        return dispute;
    }

    @Transactional(readOnly = true)
    public List<Job> todayForProvider(Long providerId, LocalDateTime dayStart, LocalDateTime dayEnd) {
        return jobRepository.findByProviderAndScheduledBetween(providerId, dayStart, dayEnd);
    }

    private void recordHistory(Long jobId, String from, String to, Long changedBy, String reason) {
        historyRepository.save(JobStatusHistory.builder()
                .jobId(jobId)
                .fromStatus(from)
                .toStatus(to)
                .changedBy(changedBy)
                .reason(reason)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private boolean canAccess(User actor, Job job, Long providerBusinessId) {
        if (actor == null || actor.getId() == null) {
            return false;
        }
        if (actor.getId().equals(job.getCustomerId())) {
            return true;
        }
        return providerBusinessId != null && providerBusinessId.equals(job.getProviderId());
    }

    private void completeLinkedBooking(Job job) {
        if (job.getBookingId() != null) {
            bookingRepository.findById(job.getBookingId()).ifPresent(booking -> {
                if (booking.getStatus() != BookingStatus.CANCELLED
                        && booking.getStatus() != BookingStatus.NO_SHOW) {
                    booking.setStatus(BookingStatus.COMPLETED);
                    bookingRepository.save(booking);
                }
            });
        }
        if (job.getServiceRequestId() != null) {
            serviceRequestRepository.findById(job.getServiceRequestId()).ifPresent(request -> {
                request.setStatus("COMPLETED");
                serviceRequestRepository.save(request);
            });
        }
    }
}

