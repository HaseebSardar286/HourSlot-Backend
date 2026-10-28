package com.duemet.request.jobs;

import com.duemet.identity.repository.UserRepository;
import com.duemet.notification.services.NotificationService;
import com.duemet.quote.repository.QuoteRepository;
import com.duemet.request.model.ServiceRequest;
import com.duemet.request.repository.ServiceRequestRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceExpirationJob {
    private static final Logger log = LogManager.getLogger(MarketplaceExpirationJob.class);

    private final ServiceRequestRepository requestRepository;
    private final QuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MarketplaceExpirationJob(
            ServiceRequestRepository requestRepository,
            QuoteRepository quoteRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.quoteRepository = quoteRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${duemet.marketplace.expire-cron:0 */15 * * * *}")
    public void expireStale() {
        int quotes = quoteRepository.expireStale();
        int requests = requestRepository.expireOpenPastDue();
        if (quotes > 0 || requests > 0) {
            log.info("Expired marketplace items quotes={} requests={}", quotes, requests);
        }
        if (requests > 0) {
            for (ServiceRequest request : requestRepository.findExpiredForNotify()) {
                userRepository.findById(request.getCustomerUserId()).ifPresent(customer ->
                        notificationService.notify(
                                customer,
                                "Request expired",
                                "Your request \"" + request.getTitle() + "\" expired before a quote was accepted."));
            }
        }
    }
}
