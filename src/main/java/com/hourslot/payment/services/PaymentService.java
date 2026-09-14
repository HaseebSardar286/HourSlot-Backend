package com.hourslot.payment.services;

import com.hourslot.booking.model.Booking;
import com.hourslot.booking.model.BookingStatus;
import com.hourslot.organization.model.Business;
import com.hourslot.catalog.model.CustomerPackage;
import com.hourslot.payment.model.Payment;
import com.hourslot.payment.model.PaymentRefund;
import com.hourslot.catalog.model.ServicePackage;
import com.hourslot.identity.model.User;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.catalog.repository.CustomerPackageRepository;
import com.hourslot.payment.repository.PaymentRefundRepository;
import com.hourslot.payment.repository.PaymentRepository;
import com.hourslot.catalog.repository.ServicePackageRepository;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.shared.util.MoneyAmounts;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import com.hourslot.notification.services.MailService;
import com.hourslot.notification.services.NotificationService;

/**
 * Stripe Checkout + webhook processing.
 * Keeps payment logic out of controllers for clarity.
 */
@Service
public class PaymentService {

    private static final Logger log = LogManager.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final CustomerPackageRepository customerPackageRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final UserRepository userRepository;
    private final MailService mailService;
    private final NotificationService notificationService;

    @Value("${app.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public PaymentService(
            BookingRepository bookingRepository,
            CustomerPackageRepository customerPackageRepository,
            ServicePackageRepository servicePackageRepository,
            PaymentRepository paymentRepository,
            PaymentRefundRepository paymentRefundRepository,
            UserRepository userRepository,
            MailService mailService,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.customerPackageRepository = customerPackageRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.notificationService = notificationService;
    }

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe SDK initialized");
        } else {
            log.warn("Stripe secret key is empty — online payments are disabled");
        }
    }

    public boolean isConfigured() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public boolean isWebhookConfigured() {
        return stripeWebhookSecret != null && !stripeWebhookSecret.isBlank();
    }

    public Map<String, String> createBookingCheckout(Long bookingId) throws StripeException {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getService() == null || booking.getBranch() == null) {
            throw new IllegalStateException("Booking is missing service or branch data");
        }

        long amountCents = MoneyAmounts.toStripeUnitAmount(booking.getPrice(), booking.getCurrency());
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Booking price must be greater than zero for online payment");
        }

        Long businessId = booking.resolvedBusiness() != null ? booking.resolvedBusiness().getId() : null;
        String successPath = businessId != null
                ? frontendBaseUrl + "/profile/book/" + businessId + "/confirmation?bookingId=" + bookingId + "&payment=ONLINE"
                : frontendBaseUrl + "/profile/bookings?payment=success";

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successPath)
                .setCancelUrl(frontendBaseUrl + "/profile/bookings?payment=cancelled")
                .putMetadata("type", "BOOKING")
                .putMetadata("bookingId", String.valueOf(bookingId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(MoneyAmounts.iso(booking.getCurrency()))
                                                .setUnitAmount(amountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Appointment — " + booking.getService().getName())
                                                                .setDescription("Branch: " + booking.getBranch().getName())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe checkout for bookingId={}, sessionId={}", bookingId, session.getId());
        return checkoutResponse(session.getUrl());
    }

    public Map<String, String> createPackageCheckout(Long packageId, Long customerUserId) throws StripeException {
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        long amountCents = MoneyAmounts.toStripeUnitAmount(servicePackage.getPrice(), servicePackage.getCurrency());
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Package price must be greater than zero for online payment");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + "/profile/packages?payment=success")
                .setCancelUrl(frontendBaseUrl + "/profile/packages?payment=cancelled")
                .putMetadata("type", "PACKAGE")
                .putMetadata("packageId", String.valueOf(packageId))
                .putMetadata("customerId", String.valueOf(customerUserId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(MoneyAmounts.iso(servicePackage.getCurrency()))
                                                .setUnitAmount(amountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Package — " + servicePackage.getName())
                                                                .setDescription(servicePackage.getSessionsCount() + " sessions")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);
        log.info("Created Stripe checkout for packageId={}, customerId={}, sessionId={}",
                packageId, customerUserId, session.getId());
        return checkoutResponse(session.getUrl());
    }

    public void handleWebhook(String payload, String signatureHeader) throws Exception {
        if (!isWebhookConfigured()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }

        Event event = Webhook.constructEvent(payload, signatureHeader, stripeWebhookSecret);
        log.info("Stripe webhook received: type={}, id={}", event.getType(), event.getId());

        if (!"checkout.session.completed".equals(event.getType())
                && !"charge.refunded".equals(event.getType())) {
            log.debug("Ignoring webhook event type={}", event.getType());
            return;
        }

        if ("charge.refunded".equals(event.getType())) {
            handleChargeRefunded(event);
            return;
        }

        Optional<Session> sessionOpt = extractCheckoutSession(event);
        if (sessionOpt.isEmpty()) {
            log.warn("Could not deserialize checkout Session from webhook event id={}", event.getId());
            return;
        }
        processCompletedSession(sessionOpt.get());
    }

    /**
     * Prefer typed deserialization; fall back to unsafe JSON only if API version differs.
     */
    private Optional<Session> extractCheckoutSession(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> object = deserializer.getObject();
        if (object.isPresent() && object.get() instanceof Session session) {
            return Optional.of(session);
        }

        try {
            StripeObject raw = deserializer.deserializeUnsafe();
            if (raw instanceof Session session) {
                log.warn("Used unsafe Stripe deserialization for event id={} (API version mismatch?)", event.getId());
                return Optional.of(session);
            }
        } catch (Exception e) {
            log.error("Unsafe Stripe deserialization failed for event id={}: {}", event.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    @Transactional
    public void processCompletedSession(Session session) {
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            log.warn("Checkout session {} has no metadata", session.getId());
            return;
        }

        String type = metadata.get("type");
        try {
            if ("BOOKING".equals(type)) {
                markBookingPaid(Long.parseLong(metadata.get("bookingId")), session);
            } else if ("PACKAGE".equals(type)) {
                activatePurchasedPackage(
                        Long.parseLong(metadata.get("packageId")),
                        Long.parseLong(metadata.get("customerId")),
                        session
                );
            } else {
                log.warn("Unknown checkout metadata type={} for session={}", type, session.getId());
            }
        } catch (NumberFormatException e) {
            log.error("Invalid metadata ids on session {}: {}", session.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Failed processing checkout session {}: {}", session.getId(), e.getMessage(), e);
            throw e;
        }
    }

    private void markBookingPaid(Long bookingId, Session session) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        booking.setPaymentStatus("PAID");
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentMethod("ONLINE");
        bookingRepository.save(booking);

        String providerPaymentId = session != null && session.getPaymentIntent() != null
                ? session.getPaymentIntent()
                : (session != null ? session.getId() : null);
        Map<String, Object> rawPayload = new HashMap<>();
        if (session != null) {
            rawPayload.put("stripeSessionId", session.getId());
            if (session.getPaymentIntent() != null) {
                rawPayload.put("stripePaymentIntent", session.getPaymentIntent());
            }
        }

        Business paidBusiness = booking.resolvedBusiness();
        paymentRepository.save(Payment.builder()
                .organization(booking.getOrganization() != null ? booking.getOrganization()
                        : (paidBusiness == null ? null : paidBusiness.getOrganization()))
                .business(paidBusiness)
                .user(booking.getCustomerUser())
                .purpose("BOOKING")
                .referenceType("BOOKING")
                .referenceId(booking.getId())
                .provider("STRIPE")
                .providerPaymentId(providerPaymentId)
                .amount(java.math.BigDecimal.valueOf(booking.getPrice()))
                .currency(booking.getCurrency() == null ? "USD" : booking.getCurrency())
                .status("SUCCEEDED")
                .rawPayload(rawPayload.isEmpty() ? null : rawPayload)
                .build());
        log.info("Booking {} marked PAID via Stripe webhook", bookingId);

        if (booking.getCustomer() == null) {
            return;
        }

        Optional<User> customerUser = userRepository.findById(booking.getCustomer().getId());
        customerUser.ifPresent(user -> {
            notificationService.notify(user, "Payment confirmed",
                    "Your payment for booking #" + bookingId + " was confirmed.");
            if (booking.getService() != null && booking.getBranch() != null) {
                mailService.sendBookingCreatedEmail(
                        user.getEmail(),
                        user.getFirstName(),
                        booking.getService().getName(),
                        String.valueOf(booking.getBookingTime()),
                        booking.getBranch().getName()
                );
            }
        });
    }

    private void activatePurchasedPackage(Long packageId, Long customerId, Session session) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        LocalDateTime expiresAt = null;
        if (servicePackage.getExpiryDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(servicePackage.getExpiryDays());
        }

        CustomerPackage customerPackage = CustomerPackage.builder()
                .customerUser(customer)
                .servicePackage(servicePackage)
                .business(servicePackage.getBusiness())
                .sessionsRemaining(servicePackage.getSessionsCount())
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();
        customerPackage = customerPackageRepository.save(customerPackage);

        String providerPaymentId = session != null && session.getPaymentIntent() != null
                ? session.getPaymentIntent()
                : (session != null ? session.getId() : null);
        Map<String, Object> rawPayload = new HashMap<>();
        if (session != null) {
            rawPayload.put("stripeSessionId", session.getId());
            if (session.getPaymentIntent() != null) {
                rawPayload.put("stripePaymentIntent", session.getPaymentIntent());
            }
        }

        paymentRepository.save(Payment.builder()
                .business(servicePackage.getBusiness())
                .organization(servicePackage.getBusiness().getOrganization())
                .user(customer)
                .purpose("PACKAGE")
                .referenceType("CUSTOMER_PACKAGE")
                .referenceId(customerPackage.getId())
                .provider("STRIPE")
                .providerPaymentId(providerPaymentId)
                .amount(java.math.BigDecimal.valueOf(servicePackage.getPrice()))
                .currency(servicePackage.getCurrency() == null ? "USD" : servicePackage.getCurrency())
                .status("SUCCEEDED")
                .rawPayload(rawPayload.isEmpty() ? null : rawPayload)
                .build());
        log.info("Activated package {} for customer {} via Stripe webhook", packageId, customerId);

        userRepository.findById(customerId).ifPresent(user -> {
            notificationService.notify(user, "Package purchased",
                    "Your package \"" + servicePackage.getName() + "\" is now active.");
            mailService.sendPackagePurchaseEmail(
                    user.getEmail(),
                    user.getFirstName(),
                    servicePackage.getName(),
                    servicePackage.getPrice(),
                    servicePackage.getSessionsCount()
            );
        });
    }

    @Transactional
    void handleChargeRefunded(Event event) {
        Optional<Charge> chargeOpt = extractCharge(event);
        if (chargeOpt.isEmpty()) {
            log.warn("Could not deserialize Charge from refund webhook event id={}", event.getId());
            return;
        }
        Charge charge = chargeOpt.get();
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            return;
        }

        paymentRepository.findByProviderPaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            Refund latestRefund = charge.getRefunds() != null && !charge.getRefunds().getData().isEmpty()
                    ? charge.getRefunds().getData().get(0)
                    : null;
            String providerRefundId = latestRefund != null ? latestRefund.getId() : event.getId();
            if (paymentRefundRepository.findByProviderRefundId(providerRefundId).isPresent()) {
                return;
            }
            java.math.BigDecimal refundAmount = latestRefund != null && latestRefund.getAmount() != null
                    ? java.math.BigDecimal.valueOf(latestRefund.getAmount()).movePointLeft(2)
                    : payment.getAmount();
            paymentRefundRepository.save(PaymentRefund.builder()
                    .payment(payment)
                    .amount(refundAmount)
                    .reason("Stripe refund")
                    .providerRefundId(providerRefundId)
                    .status("SUCCEEDED")
                    .build());
            payment.setStatus("REFUNDED");
            paymentRepository.save(payment);
            log.info("Recorded refund for payment id={}", payment.getId());
        }, () -> log.warn("No payment found for Stripe paymentIntent={}", paymentIntentId));
    }

    private Optional<Charge> extractCharge(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> object = deserializer.getObject();
        if (object.isPresent() && object.get() instanceof Charge charge) {
            return Optional.of(charge);
        }
        try {
            StripeObject raw = deserializer.deserializeUnsafe();
            if (raw instanceof Charge charge) {
                return Optional.of(charge);
            }
        } catch (Exception e) {
            log.error("Unsafe Stripe Charge deserialization failed for event id={}: {}", event.getId(), e.getMessage());
        }
        return Optional.empty();
    }

    private static Map<String, String> checkoutResponse(String url) {
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return response;
    }
}
