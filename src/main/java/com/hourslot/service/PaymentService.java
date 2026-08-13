package com.hourslot.service;

import com.hourslot.model.Booking;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.Customer;
import com.hourslot.model.CustomerPackage;
import com.hourslot.model.ServicePackage;
import com.hourslot.model.User;
import com.hourslot.repository.BookingRepository;
import com.hourslot.repository.CustomerPackageRepository;
import com.hourslot.repository.CustomerRepository;
import com.hourslot.repository.ServicePackageRepository;
import com.hourslot.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
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
    private final CustomerRepository customerRepository;
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
            CustomerRepository customerRepository,
            UserRepository userRepository,
            MailService mailService,
            NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.customerPackageRepository = customerPackageRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.customerRepository = customerRepository;
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        if (booking.getService() == null || booking.getBranch() == null) {
            throw new IllegalStateException("Booking is missing service or branch data");
        }

        long amountCents = Math.round(booking.getPrice() * 100);
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Booking price must be greater than zero for online payment");
        }

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + "/profile/bookings?payment=success")
                .setCancelUrl(frontendBaseUrl + "/profile/bookings?payment=cancelled")
                .putMetadata("type", "BOOKING")
                .putMetadata("bookingId", String.valueOf(bookingId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
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

        long amountCents = Math.round(servicePackage.getPrice() * 100);
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
                                                .setCurrency("usd")
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

        if (!"checkout.session.completed".equals(event.getType())) {
            log.debug("Ignoring webhook event type={}", event.getType());
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
                markBookingPaid(Long.parseLong(metadata.get("bookingId")));
            } else if ("PACKAGE".equals(type)) {
                activatePurchasedPackage(
                        Long.parseLong(metadata.get("packageId")),
                        Long.parseLong(metadata.get("customerId"))
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

    private void markBookingPaid(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));

        booking.setPaymentStatus("PAID");
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
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

    private void activatePurchasedPackage(Long packageId, Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found: " + packageId));

        LocalDateTime expiresAt = null;
        if (servicePackage.getExpiryDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(servicePackage.getExpiryDays());
        }

        CustomerPackage customerPackage = CustomerPackage.builder()
                .customer(customer)
                .servicePackage(servicePackage)
                .sessionsRemaining(servicePackage.getSessionsCount())
                .expiresAt(expiresAt)
                .status("ACTIVE")
                .build();
        customerPackageRepository.save(customerPackage);
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

    private static Map<String, String> checkoutResponse(String url) {
        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        return response;
    }
}
