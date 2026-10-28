package com.duemet.booking.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.model.BookingStatusHistory;
import com.duemet.booking.repository.BookingIncidentRepository;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingStatusHistoryRepository;
import com.duemet.booking.repository.BusinessPolicyRepository;
import com.duemet.booking.repository.BusinessPolicyRepository.BusinessPolicy;
import com.duemet.booking.repository.WaitlistRepository;
import com.duemet.catalog.model.CustomerPackage;
import com.duemet.catalog.repository.CustomerPackageRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.notification.services.MailService;
import com.duemet.notification.services.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Cancellations and no-shows, applied against the business's policy. Every path that cancels a booking
 * goes through here so packages are refunded, the incident is recorded and waiting customers are told.
 * Fees are recorded as owed for the provider to collect; nothing is charged automatically.
 */
@Service
public class BookingPolicyService {

    private static final Logger log = LogManager.getLogger(BookingPolicyService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm");

    public record CancelResult(boolean late, BigDecimal fee, String currency) {
    }

    private final BookingRepository bookingRepository;
    private final BusinessPolicyRepository policyRepository;
    private final BookingIncidentRepository incidentRepository;
    private final WaitlistRepository waitlistRepository;
    private final CustomerPackageRepository customerPackageRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final String frontendBaseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    private com.duemet.marketing.PromoCodeService promoCodeService;

    public BookingPolicyService(BookingRepository bookingRepository,
                                BusinessPolicyRepository policyRepository,
                                BookingIncidentRepository incidentRepository,
                                WaitlistRepository waitlistRepository,
                                CustomerPackageRepository customerPackageRepository,
                                BookingStatusHistoryRepository historyRepository,
                                UserRepository userRepository,
                                NotificationService notificationService,
                                MailService mailService,
                                @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.bookingRepository = bookingRepository;
        this.policyRepository = policyRepository;
        this.incidentRepository = incidentRepository;
        this.waitlistRepository = waitlistRepository;
        this.customerPackageRepository = customerPackageRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.mailService = mailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public BusinessPolicy policyFor(Long businessId) {
        return policyRepository.find(businessId);
    }

    /** What cancelling now would cost the customer, without cancelling. */
    public CancelResult previewCustomerCancel(Booking booking) {
        BusinessPolicy policy = policyRepository.find(businessId(booking));
        double hours = hoursBefore(booking);
        boolean late = isLate(policy, hours);
        return new CancelResult(late, late ? fee(booking, policy.lateCancelFeePercent()) : BigDecimal.ZERO,
                booking.getCurrency());
    }

    @Transactional
    public CancelResult cancelByCustomer(Booking booking, User customer, String reason) {
        requireCancellable(booking);
        BusinessPolicy policy = policyRepository.find(businessId(booking));
        double hours = hoursBefore(booking);
        boolean late = isLate(policy, hours);
        BigDecimal fee = late ? fee(booking, policy.lateCancelFeePercent()) : BigDecimal.ZERO;
        applyCancel(booking, customer, late ? "CUSTOMER_LATE_CANCEL" : "CUSTOMER_CANCEL");
        incidentRepository.record(booking.getId(), businessId(booking), customerId(booking),
                late ? BookingIncidentRepository.CUSTOMER_LATE_CANCEL : BookingIncidentRepository.CUSTOMER_CANCEL,
                BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP), fee, reason, customer.getId());
        notifyWaitlist(booking);
        return new CancelResult(late, fee, booking.getCurrency());
    }

    @Transactional
    public void cancelByProvider(Booking booking, User actor, String reason) {
        requireCancellable(booking);
        applyCancel(booking, actor, "PROVIDER_CANCEL");
        incidentRepository.record(booking.getId(), businessId(booking), customerId(booking),
                BookingIncidentRepository.PROVIDER_CANCEL,
                BigDecimal.valueOf(hoursBefore(booking)).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO, reason, actor == null ? null : actor.getId());
        notifyWaitlist(booking);
    }

    /** Provider marks that the customer did not turn up. Only possible once the appointment has started. */
    @Transactional
    public BigDecimal markNoShow(Booking booking, User actor) {
        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only upcoming bookings can be marked as a no-show.");
        }
        if (booking.getBookingTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("A booking can be marked as a no-show once its start time has passed.");
        }
        BusinessPolicy policy = policyRepository.find(businessId(booking));
        BigDecimal fee = fee(booking, policy.noShowFeePercent());
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);
        historyRepository.save(BookingStatusHistory.builder()
                .booking(booking).fromStatus(previous.name()).toStatus(BookingStatus.NO_SHOW.name())
                .changedBy(actor).reason("NO_SHOW").build());
        incidentRepository.record(booking.getId(), businessId(booking), customerId(booking),
                BookingIncidentRepository.NO_SHOW, BigDecimal.ZERO, fee, null, actor == null ? null : actor.getId());
        return fee;
    }

    /**
     * Customers who repeatedly miss appointments at a business must pay online up front there,
     * if the business turned that rule on.
     */
    public void requirePrepaymentIfNeeded(Long customerId, Long businessId, String paymentMethod) {
        BusinessPolicy policy = policyRepository.find(businessId);
        if (policy.prepayAfterNoShows() <= 0 || "ONLINE".equals(paymentMethod) || "PACKAGE".equals(paymentMethod)) {
            return;
        }
        if (incidentRepository.countNoShows(customerId, businessId) >= policy.prepayAfterNoShows()) {
            throw new IllegalArgumentException(
                    "Because of earlier missed appointments, this provider asks you to pay online when booking.");
        }
    }

    private void applyCancel(Booking booking, User actor, String reason) {
        CustomerPackage cp = booking.getCustomerPackage();
        if (cp != null) {
            cp.setSessionsRemaining(cp.getSessionsRemaining() + 1);
            if ("EXHAUSTED".equals(cp.getStatus()) || "EXPIRED".equals(cp.getStatus())) {
                cp.setStatus("ACTIVE");
            }
            customerPackageRepository.save(cp);
        }
        promoCodeService.release(booking.getId());
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        historyRepository.save(BookingStatusHistory.builder()
                .booking(booking).fromStatus(previous.name()).toStatus(BookingStatus.CANCELLED.name())
                .changedBy(actor).reason(reason).build());
    }

    /** Tell customers waiting for this branch/service/day that a slot opened up. First to book gets it. */
    void notifyWaitlist(Booking booking) {
        if (booking.getBranch() == null || booking.getService() == null || booking.getBookingTime() == null
                || booking.getBookingTime().isBefore(LocalDateTime.now())) {
            return;
        }
        var entries = waitlistRepository.findActiveFor(booking.getBranch().getId(), booking.getService().getId(),
                booking.getBookingTime().toLocalDate());
        for (var entry : entries) {
            User user = userRepository.findById(entry.customerUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            String when = booking.getBookingTime().format(WHEN);
            String link = frontendBaseUrl + "/profile/book/" + entry.businessId()
                    + "?serviceId=" + entry.serviceId() + "&branchId=" + entry.branchId()
                    + "&date=" + entry.desiredDate() + "&step=schedule";
            notificationService.notify(user, "WAITLIST_SLOT_OPEN", "A slot opened up",
                    entry.serviceName() + " at " + entry.businessName() + " — " + when + " is now free.", entry.id());
            mailService.sendEmail(user.getEmail(), "A slot opened up at " + entry.businessName(),
                    "<p>Good news — a " + entry.serviceName() + " slot at " + entry.businessName()
                            + " on " + when + " just became available.</p>"
                            + "<p><a href=\"" + link + "\">Book it now</a> — the first person to book gets it.</p>");
            waitlistRepository.markNotified(entry.id());
        }
        if (!entries.isEmpty()) {
            log.info("Notified {} waitlisted customers for booking {}", entries.size(), booking.getId());
        }
    }

    private static void requireCancellable(Booking booking) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Cannot cancel a booking in status " + booking.getStatus() + ".");
        }
    }

    static boolean isLate(BusinessPolicy policy, double hoursBefore) {
        return policy.freeCancelHours() > 0 && hoursBefore < policy.freeCancelHours();
    }

    static double hoursBefore(Booking booking) {
        return Duration.between(LocalDateTime.now(), booking.getBookingTime()).toMinutes() / 60.0;
    }

    static BigDecimal fee(Booking booking, BigDecimal percent) {
        if (percent == null || percent.signum() <= 0 || booking.getTotalPrice() == null) {
            return BigDecimal.ZERO;
        }
        return booking.getTotalPrice().multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static Long businessId(Booking booking) {
        if (booking.resolvedBusiness() != null) {
            return booking.resolvedBusiness().getId();
        }
        return booking.getBranch() != null && booking.getBranch().getBusiness() != null
                ? booking.getBranch().getBusiness().getId() : null;
    }

    private static Long customerId(Booking booking) {
        return booking.getCustomerUser() != null ? booking.getCustomerUser().getId()
                : booking.getCustomer() != null ? booking.getCustomer().getId() : null;
    }
}
