package com.duemet.job.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.catalog.model.Service;
import com.duemet.identity.model.User;
import com.duemet.job.model.Dispute;
import com.duemet.job.model.Job;
import com.duemet.job.model.JobMedia;
import com.duemet.job.model.JobNote;
import com.duemet.job.model.JobMaterial;
import com.duemet.job.repository.JobMaterialRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.payment.model.Payment;
import com.duemet.payment.repository.PaymentRepository;
import com.duemet.job.model.JobStatusHistory;
import com.duemet.job.repository.DisputeRepository;
import com.duemet.job.repository.JobMediaRepository;
import com.duemet.job.repository.JobNoteRepository;
import com.duemet.job.repository.JobRepository;
import com.duemet.job.repository.JobStatusHistoryRepository;
import com.duemet.request.repository.ServiceRequestRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.AuditService;
import com.duemet.notification.services.NotificationService;
import com.duemet.identity.repository.UserRepository;
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
    private final JobMaterialRepository materialRepository;
    private final PaymentRepository paymentRepository;
    private final BookingVisitRepository bookingVisitRepository;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.duemet.booking.services.BookingPolicyService bookingPolicyService;

    /** States in which the customer may still cancel; once the provider is on the way only they can. */
    private static final Set<String> CUSTOMER_CANCELLABLE = Set.of("REQUESTED", "ACCEPTED", "SCHEDULED");
    /** Offline payment methods a provider can record when collecting on site. */
    public static final Set<String> OFFLINE_PAYMENT_METHODS = Set.of("CASH", "BANK_TRANSFER", "WALLET");

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
            NotificationService notificationService,
            JobMaterialRepository materialRepository,
            PaymentRepository paymentRepository,
            BookingVisitRepository bookingVisitRepository) {
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
        this.materialRepository = materialRepository;
        this.paymentRepository = paymentRepository;
        this.bookingVisitRepository = bookingVisitRepository;
    }

    @Transactional
    public Job createFromBooking(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking is required.");
        }
        if (!"REQUEST".equalsIgnoreCase(booking.getSource())) {
            return null;
        }
        return createJob(booking);
    }

    /** Job for a direct home-visit booking (the provider travels to the customer). */
    @Transactional
    public Job createForVisit(Booking booking) {
        if (booking == null || booking.getId() == null) {
            throw new IllegalArgumentException("Booking is required.");
        }
        return createJob(booking);
    }

    private Job createJob(Booking booking) {
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

        String requestTitle = booking.getServiceRequestId() == null ? null
                : serviceRequestRepository.findById(booking.getServiceRequestId())
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
        List<JobMaterial> materials = materialRepository.findByJob(jobId);
        map.put("materials", materials);
        map.put("materialsTotal", materials.stream().map(JobMaterial::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        if (job.getBookingId() != null) {
            bookingVisitRepository.findByBookingId(job.getBookingId()).ifPresent(v -> map.put("visit", v));
            bookingRepository.findById(job.getBookingId()).ifPresent(b -> {
                map.put("paymentStatus", b.getPaymentStatus());
                map.put("paymentMethod", b.getPaymentMethod());
            });
        }
        map.put("viewerIsProvider", isProvider(job, providerBusinessId));
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
        if (!isProvider(job, providerBusinessId)
                && !("CANCELLED".equals(target) && CUSTOMER_CANCELLABLE.contains(current))) {
            throw new SecurityException("Only the provider can update the progress of this job.");
        }
        if ("COMPLETED".equals(target) && finalAmount != null) {
            if (finalAmount.signum() < 0) {
                throw new IllegalArgumentException("Final amount cannot be negative.");
            }
            job.setFinalAmount(finalAmount);
        } else if ("COMPLETED".equals(target) && job.getFinalAmount() == null) {
            BigDecimal labour = job.getEstimatedAmount() == null ? BigDecimal.ZERO : job.getEstimatedAmount();
            job.setFinalAmount(labour.add(materialRepository.totalForJob(jobId)));
        }
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case "IN_PROGRESS" -> job.setStartedAt(now);
            case "COMPLETED" -> {
                job.setCompletedAt(now);
                completeLinkedBooking(job);
            }
            case "CANCELLED" -> {
                job.setCancelledAt(now);
                cancelLinkedBooking(job, actor, isProvider(job, providerBusinessId), reason);
            }
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
        String current = job.getStatus() == null ? "" : job.getStatus().toUpperCase();
        if (!Set.of("IN_PROGRESS", "AWAITING_PAYMENT", "COMPLETED").contains(current)) {
            throw new IllegalStateException("Disputes can only be opened for in-progress, awaiting-payment, or completed jobs.");
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

    @Transactional
    public JobMaterial addMaterial(Long jobId, User actor, Long providerBusinessId,
                                   String name, BigDecimal quantity, BigDecimal unitPrice) {
        Job job = requireProvider(actor, jobId, providerBusinessId);
        if (Set.of("COMPLETED", "CANCELLED", "DISPUTED").contains(job.getStatus().toUpperCase())) {
            throw new IllegalStateException("Materials can only be added before the job is completed.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Material name is required.");
        }
        BigDecimal qty = quantity == null ? BigDecimal.ONE : quantity;
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        if (qty.signum() <= 0 || price.signum() < 0) {
            throw new IllegalArgumentException("Quantity must be positive and price cannot be negative.");
        }
        return materialRepository.save(JobMaterial.builder()
                .jobId(jobId)
                .name(name.trim())
                .quantity(qty)
                .unitPrice(price)
                .build());
    }

    @Transactional
    public void removeMaterial(Long jobId, Long materialId, User actor, Long providerBusinessId) {
        Job job = requireProvider(actor, jobId, providerBusinessId);
        if (Set.of("COMPLETED", "CANCELLED", "DISPUTED").contains(job.getStatus().toUpperCase())) {
            throw new IllegalStateException("Materials cannot be changed after the job is completed.");
        }
        if (materialRepository.delete(jobId, materialId) == 0) {
            throw new RuntimeException("Material not found.");
        }
    }

    /** Customer signs off that the work was done as agreed. */
    @Transactional
    public Job confirmCompletion(Long jobId, User actor) {
        Job job = requireAccessible(actor, jobId, null);
        if (!actor.getId().equals(job.getCustomerId())) {
            throw new SecurityException("Only the customer can confirm the work.");
        }
        if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
            throw new IllegalStateException("The job can be confirmed once the provider marks it completed.");
        }
        if (job.getCustomerConfirmedAt() == null) {
            job.setCustomerConfirmedAt(LocalDateTime.now());
            jobRepository.save(job);
            recordHistory(jobId, job.getStatus(), job.getStatus(), actor.getId(), "Customer confirmed completion");
        }
        return job;
    }

    /** Provider records money collected on site (cash on delivery, bank transfer, wallet). */
    @Transactional
    public Job collectPayment(Long jobId, User actor, Long providerBusinessId, BigDecimal amount, String method) {
        Job job = requireProvider(actor, jobId, providerBusinessId);
        String status = job.getStatus().toUpperCase();
        if (!"COMPLETED".equals(status) && !"AWAITING_PAYMENT".equals(status)) {
            throw new IllegalStateException("Payment can be recorded once the work is finished.");
        }
        String normalized = method == null || method.isBlank() ? "CASH" : method.trim().toUpperCase();
        if (!OFFLINE_PAYMENT_METHODS.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported payment method: " + method);
        }
        BigDecimal due = job.getFinalAmount() != null ? job.getFinalAmount() : job.getEstimatedAmount();
        BigDecimal collected = amount != null ? amount : due;
        if (collected == null || collected.signum() <= 0) {
            throw new IllegalArgumentException("Collected amount must be positive.");
        }
        Booking booking = job.getBookingId() == null ? null
                : bookingRepository.findById(job.getBookingId()).orElse(null);
        if (booking != null && "PAID".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("Payment for this job was already recorded.");
        }
        Business provider = businessRepository.findById(job.getProviderId()).orElse(null);
        paymentRepository.save(Payment.builder()
                .organization(provider == null ? null : provider.getOrganization())
                .business(provider)
                .user(userRepository.findById(job.getCustomerId()).orElse(null))
                .purpose("BOOKING")
                .referenceType(booking != null ? "BOOKING" : "JOB")
                .referenceId(booking != null ? booking.getId() : job.getId())
                .provider(normalized)
                .amount(collected)
                .currency(job.getCurrency() == null ? "PKR" : job.getCurrency())
                .status("SUCCEEDED")
                .build());
        if (booking != null) {
            booking.setPaymentStatus("PAID");
            booking.setPaymentMethod(normalized);
            bookingRepository.save(booking);
        }
        if ("AWAITING_PAYMENT".equals(status)) {
            recordHistory(jobId, status, "COMPLETED", actor.getId(), "Payment collected (" + normalized + ")");
            job.setStatus("COMPLETED");
            if (job.getCompletedAt() == null) {
                job.setCompletedAt(LocalDateTime.now());
            }
            jobRepository.save(job);
            completeLinkedBooking(job);
        } else {
            recordHistory(jobId, status, status, actor.getId(), "Payment collected (" + normalized + ")");
        }
        auditService.log(actor, provider, "JOB_PAYMENT_COLLECTED", "Job", jobId, normalized + " " + collected);
        return job;
    }

    private Job requireProvider(User actor, Long jobId, Long providerBusinessId) {
        Job job = requireAccessible(actor, jobId, providerBusinessId);
        if (!isProvider(job, providerBusinessId)) {
            throw new SecurityException("Only the provider can do this.");
        }
        return job;
    }

    private static boolean isProvider(Job job, Long providerBusinessId) {
        return providerBusinessId != null && providerBusinessId.equals(job.getProviderId());
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

    private void cancelLinkedBooking(Job job, User actor, boolean byProvider, String reason) {
        if (job.getBookingId() != null) {
            bookingRepository.findByIdWithDetails(job.getBookingId()).ifPresent(booking -> {
                if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED) {
                    // Same policy, incident record and waitlist notice as cancelling the booking directly.
                    if (byProvider) {
                        bookingPolicyService.cancelByProvider(booking, actor, reason);
                    } else {
                        bookingPolicyService.cancelByCustomer(booking, actor, reason);
                    }
                } else if (booking.getStatus() != BookingStatus.COMPLETED
                        && booking.getStatus() != BookingStatus.NO_SHOW
                        && booking.getStatus() != BookingStatus.CANCELLED) {
                    booking.setStatus(BookingStatus.CANCELLED);
                    bookingRepository.save(booking);
                }
            });
        }
        if (job.getServiceRequestId() != null) {
            serviceRequestRepository.findById(job.getServiceRequestId()).ifPresent(request -> {
                if (!"COMPLETED".equalsIgnoreCase(request.getStatus())
                        && !"CANCELLED".equalsIgnoreCase(request.getStatus())) {
                    request.setStatus("CANCELLED");
                    serviceRequestRepository.save(request);
                }
            });
        }
    }
}

