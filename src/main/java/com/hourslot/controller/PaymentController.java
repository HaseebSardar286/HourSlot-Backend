package com.hourslot.controller;

import com.hourslot.dto.MessageResponse;
import com.hourslot.security.CustomUserDetails;
import com.hourslot.service.PaymentService;
import com.stripe.exception.StripeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LogManager.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> createCheckoutSession(
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) Long packageId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (!paymentService.isConfigured()) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Online payments are not configured on this server."));
        }

        if (bookingId == null && packageId == null) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse("Provide bookingId or packageId."));
        }

        try {
            Map<String, String> session;
            if (bookingId != null) {
                log.info("Checkout requested for bookingId={} by userId={}", bookingId, userDetails.getId());
                session = paymentService.createBookingCheckout(bookingId);
            } else {
                log.info("Checkout requested for packageId={} by userId={}", packageId, userDetails.getId());
                session = paymentService.createPackageCheckout(packageId, userDetails.getId());
            }
            return ResponseEntity.ok(session);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Checkout rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        } catch (StripeException e) {
            log.error("Stripe checkout failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new MessageResponse("Payment provider error. Please try again or pay at venue."));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (sigHeader == null || sigHeader.isBlank()) {
            return ResponseEntity.badRequest().body("Missing Stripe-Signature header");
        }

        try {
            paymentService.handleWebhook(payload, sigHeader);
            return ResponseEntity.ok("ok");
        } catch (IllegalStateException e) {
            log.warn("Webhook rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Webhook processing failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid webhook");
        }
    }
}
