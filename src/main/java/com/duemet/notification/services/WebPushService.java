package com.duemet.notification.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.duemet.notification.repository.PushSubscriptionRepository;
import com.duemet.notification.repository.PushSubscriptionRepository.PushSubscription;
import jakarta.annotation.PreDestroy;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends browser push notifications (Chrome, Edge, Firefox, Android, and iOS 16.4+ home-screen apps).
 * Disabled unless VAPID keys are configured. Sending runs on a background thread so a slow push
 * service never delays the request that triggered the notification.
 */
@Service
public class WebPushService {

    private static final Logger log = LogManager.getLogger(WebPushService.class);

    private final PushSubscriptionRepository subscriptions;
    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final PushService pushService;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "web-push");
        t.setDaemon(true);
        return t;
    });

    public WebPushService(PushSubscriptionRepository subscriptions,
                          ObjectMapper objectMapper,
                          @Value("${app.push.vapid-public-key:}") String publicKey,
                          @Value("${app.push.vapid-private-key:}") String privateKey,
                          @Value("${app.push.vapid-subject:}") String subject,
                          @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        PushService service = null;
        if (!this.publicKey.isEmpty() && privateKey != null && !privateKey.isBlank()) {
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                String contact = subject == null || subject.isBlank() ? frontendBaseUrl : subject.trim();
                service = new PushService(this.publicKey, privateKey.trim(), contact);
                log.info("Web push enabled");
            } catch (Exception ex) {
                log.error("Web push disabled — invalid VAPID keys: {}", ex.getMessage());
            }
        } else {
            log.info("Web push disabled — VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY not set");
        }
        this.pushService = service;
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String publicKey() {
        return publicKey;
    }

    /** Push to every device the user enabled notifications on. Returns immediately. */
    public void sendToUser(Long userId, String title, String body, String url) {
        if (!isEnabled() || userId == null) {
            return;
        }
        executor.submit(() -> {
            for (PushSubscription sub : subscriptions.findByUser(userId)) {
                deliver(sub, title, body, url);
            }
        });
    }

    void deliver(PushSubscription sub, String title, String body, String url) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("url", url == null ? "/" : url);
            Notification notification = new Notification(sub.endpoint(), sub.p256dh(), sub.auth(),
                    objectMapper.writeValueAsBytes(payload));
            // aes128gcm (RFC 8291) is required by Apple's push service and supported by all current browsers.
            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                // The browser unsubscribed or the subscription expired; stop sending to it.
                subscriptions.delete(sub.id());
                log.info("Removed expired push subscription id={}", sub.id());
            } else if (status >= 400) {
                log.warn("Push to subscription id={} failed with HTTP {}", sub.id(), status);
            } else {
                subscriptions.touch(sub.id());
            }
        } catch (Exception ex) {
            log.warn("Push to subscription id={} failed: {}", sub.id(), ex.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
