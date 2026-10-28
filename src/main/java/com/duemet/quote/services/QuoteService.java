package com.duemet.quote.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.services.BookingService;
import com.duemet.catalog.model.Service;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.job.services.JobService;
import com.duemet.notification.services.NotificationService;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.AuditService;
import com.duemet.organization.services.EntitlementService;
import com.duemet.organization.services.UsageCounterService;
import com.duemet.quote.QuoteAcceptGuard;
import com.duemet.quote.model.Quote;
import com.duemet.quote.repository.QuoteRepository;
import com.duemet.request.model.ServiceRequest;
import com.duemet.request.model.ServiceRequestProvider;
import com.duemet.request.repository.ServiceRequestProviderRepository;
import com.duemet.request.repository.ServiceRequestRepository;
import com.duemet.request.services.RequestMatchingService;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@org.springframework.stereotype.Service
public class QuoteService {
    private final QuoteRepository quoteRepository;
    private final ServiceRequestRepository requestRepository;
    private final ServiceRequestProviderRepository providerLinkRepository;
    private final BookingService bookingService;
    private final BranchRepository branchRepository;
    private final BusinessRepository businessRepository;
    private final ServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final RequestMatchingService matchingService;
    private final JobService jobService;
    private final UsageCounterService usageCounterService;
    private final EntitlementService entitlementService;
    private final AuditService auditService;

    public QuoteService(
            QuoteRepository quoteRepository,
            ServiceRequestRepository requestRepository,
            ServiceRequestProviderRepository providerLinkRepository,
            BookingService bookingService,
            BranchRepository branchRepository,
            BusinessRepository businessRepository,
            ServiceRepository serviceRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            RequestMatchingService matchingService,
            JobService jobService,
            UsageCounterService usageCounterService,
            EntitlementService entitlementService,
            AuditService auditService) {
        this.quoteRepository = quoteRepository;
        this.requestRepository = requestRepository;
        this.providerLinkRepository = providerLinkRepository;
        this.bookingService = bookingService;
        this.branchRepository = branchRepository;
        this.businessRepository = businessRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.matchingService = matchingService;
        this.jobService = jobService;
        this.usageCounterService = usageCounterService;
        this.entitlementService = entitlementService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Quote> listForRequest(Long requestId) {
        return quoteRepository.findByRequest(requestId);
    }

    @Transactional
    public Quote submit(Business provider, Long requestId, BigDecimal amount, String currency,
                        String description, Integer estimatedDurationMinutes) {
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Service request not found."));
        if ("CANCELLED".equalsIgnoreCase(request.getStatus())
                || "EXPIRED".equalsIgnoreCase(request.getStatus())
                || "ACCEPTED".equalsIgnoreCase(request.getStatus())
                || "SCHEDULED".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Cannot quote a request in status " + request.getStatus());
        }
        if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This request has expired.");
        }
        ServiceRequestProvider link = providerLinkRepository.findByRequestAndProvider(requestId, provider.getId())
                .orElseThrow(() -> new IllegalStateException("Provider was not invited to this request."));
        if ("DECLINED".equalsIgnoreCase(link.getResponseStatus())) {
            throw new IllegalStateException("Provider already declined this request.");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Quote amount is required.");
        }
        Business billedProvider = businessRepository.findById(provider.getId()).orElse(provider);
        if (billedProvider.getOrganization() != null) {
            long used = usageCounterService.currentCount(billedProvider.getOrganization(), "quotes_sent");
            entitlementService.requireHeadroom(
                    billedProvider.getOrganization(), EntitlementService.MAX_QUOTES_MONTHLY, used, "quotes this month");
        }
        Quote quote = quoteRepository.save(Quote.builder()
                .requestId(requestId)
                .providerId(provider.getId())
                .amount(amount)
                .currency(currency == null || currency.isBlank() ? request.getCurrency() : currency)
                .description(description)
                .estimatedDurationMinutes(estimatedDurationMinutes)
                .status("SENT")
                .validUntil(LocalDateTime.now().plusDays(7))
                .build());
        link.setResponseStatus("ACCEPTED");
        link.setRespondedAt(LocalDateTime.now());
        providerLinkRepository.save(link);
        request.setStatus("QUOTED");
        requestRepository.save(request);
        businessRepository.findById(provider.getId()).ifPresent(full -> {
            if (full.getOrganization() != null) {
                usageCounterService.increment(full.getOrganization(), "quotes_sent");
            }
        });
        userRepository.findById(request.getCustomerUserId()).ifPresent(customer ->
                notificationService.notify(customer, "QUOTE_RECEIVED", "New quote received",
                        provider.getName() + " sent a quote for " + request.getTitle(), quote.getId()));
        auditService.log(null, provider, "QUOTE_SUBMITTED", "Quote", quote.getId(), request.getTitle());
        return quote;
    }

    @Transactional
    public Booking accept(User customer, Long requestId, Long quoteId, LocalDateTime bookingTime) {
        ServiceRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Service request not found."));
        if (!customer.getId().equals(request.getCustomerUserId())) {
            throw new SecurityException("Not allowed to accept this request.");
        }
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Quote not found."));
        if (!requestId.equals(quote.getRequestId())) {
            throw new IllegalArgumentException("Quote does not belong to this request.");
        }
        if (QuoteAcceptGuard.isExpired(quote, request)) {
            throw new IllegalStateException("This quote or request has expired.");
        }
        if (!QuoteAcceptGuard.isAcceptableStatus(quote.getStatus())) {
            throw new IllegalStateException("Quote cannot be accepted in status " + quote.getStatus());
        }

        int claimedQuote = quoteRepository.markAcceptedIfOpen(quote.getId());
        if (claimedQuote == 0) {
            throw new IllegalStateException("Quote was already accepted or is no longer available.");
        }
        quote.setStatus("ACCEPTED");

        List<Branch> branches = branchRepository.findByBusiness(
                Business.builder().id(quote.getProviderId()).build());
        if (branches.isEmpty()) {
            throw new IllegalStateException("Provider has no bookable branch.");
        }
        Branch branch = branches.get(0);
        branch.setBusiness(Business.builder().id(quote.getProviderId()).build());

        Long serviceId = request.getServiceId();
        if (serviceId == null) {
            List<Service> services = serviceRepository.findByBusiness(
                    Business.builder().id(quote.getProviderId()).build());
            serviceId = services.stream()
                    .filter(Service::isActive)
                    .map(Service::getId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No service found on provider to attach the booking."));
        }

        LocalDateTime scheduled = bookingTime != null ? bookingTime : resolvePreferredTime(request);
        Booking booking = bookingService.createBookingFromRequest(
                customer.getId(),
                branch.getId(),
                serviceId,
                null,
                scheduled,
                "Accepted quote #" + quote.getId(),
                request.getId(),
                quote.getId(),
                quote.getAmount());

        // Atomic request claim — if another quote already won, roll back this transaction.
        int claimedRequest = requestRepository.claimForScheduling(
                requestId, quote.getId(), quote.getProviderId(), booking.getId());
        if (claimedRequest == 0) {
            throw new IllegalStateException("This request was already scheduled with another quote.");
        }

        quoteRepository.rejectOthers(requestId, quote.getId());

        request.setStatus("SCHEDULED");
        request.setSelectedProviderId(quote.getProviderId());
        request.setSelectedQuoteId(quote.getId());
        request.setBookingId(booking.getId());

        matchingService.notifyCustomer(customer, "Quote accepted",
                "Your booking " + booking.getPublicCode() + " is confirmed.");
        businessRepository.findById(quote.getProviderId()).ifPresent(provider ->
                auditService.log(customer, provider, "QUOTE_ACCEPTED", "Quote", quote.getId(), booking.getPublicCode()));
        if ("REQUEST".equalsIgnoreCase(booking.getSource())) {
            jobService.createFromBooking(booking);
        }
        return booking;
    }

    private LocalDateTime resolvePreferredTime(ServiceRequest request) {
        LocalDate date = request.getPreferredDate() != null
                ? request.getPreferredDate()
                : LocalDate.now().plusDays(2);
        LocalTime time = request.getPreferredTimeFrom() != null
                ? request.getPreferredTimeFrom()
                : LocalTime.of(10, 0);
        return LocalDateTime.of(date, time);
    }
}
