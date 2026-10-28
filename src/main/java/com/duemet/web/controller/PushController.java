package com.duemet.web.controller;

import com.duemet.identity.security.CustomUserDetails;
import com.duemet.notification.repository.PushSubscriptionRepository;
import com.duemet.notification.services.WebPushService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Browser push: the public VAPID key, and saving / removing a device's subscription. */
@RestController
public class PushController {

    private final WebPushService webPushService;
    private final PushSubscriptionRepository subscriptions;

    public PushController(WebPushService webPushService, PushSubscriptionRepository subscriptions) {
        this.webPushService = webPushService;
        this.subscriptions = subscriptions;
    }

    @GetMapping("/api/public/push/config")
    public ResponseEntity<?> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", webPushService.isEnabled());
        body.put("publicKey", webPushService.isEnabled() ? webPushService.publicKey() : null);
        return ResponseEntity.ok(body);
    }

    /** Shape of the browser's PushSubscription.toJSON(). */
    @Data
    public static class SubscriptionBody {
        @NotBlank private String endpoint;
        @NotNull @Valid private Keys keys;

        @Data
        public static class Keys {
            @NotBlank private String p256dh;
            @NotBlank private String auth;
        }
    }

    @PostMapping("/api/push/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> subscribe(@Valid @RequestBody SubscriptionBody body,
                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!webPushService.isEnabled()) {
            throw new IllegalStateException("Push notifications are not configured on this server.");
        }
        if (!body.getEndpoint().startsWith("https://")) {
            throw new IllegalArgumentException("Invalid push endpoint.");
        }
        subscriptions.upsert(userDetails.getId(), body.getEndpoint(), body.getKeys().getP256dh(),
                body.getKeys().getAuth(), userAgent);
        return ResponseEntity.ok(Map.of("subscribed", true));
    }

    @DeleteMapping("/api/push/subscriptions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> unsubscribe(@RequestParam String endpoint,
                                         @AuthenticationPrincipal CustomUserDetails userDetails) {
        subscriptions.deleteForUser(userDetails.getId(), endpoint);
        return ResponseEntity.noContent().build();
    }

    /** Lets a user check notifications reach this device right after enabling them. */
    @PostMapping("/api/push/test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> test(@AuthenticationPrincipal CustomUserDetails userDetails) {
        webPushService.sendToUser(userDetails.getId(), "Notifications are on",
                "You'll get booking updates on this device.", "/");
        return ResponseEntity.ok(Map.of("sent", true));
    }
}
