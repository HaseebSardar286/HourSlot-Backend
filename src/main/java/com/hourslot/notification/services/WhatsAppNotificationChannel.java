package com.hourslot.notification.services;

import com.hourslot.identity.model.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppNotificationChannel implements NotificationChannel {
    private static final Logger log = LogManager.getLogger(WhatsAppNotificationChannel.class);

    private final boolean enabled;

    public WhatsAppNotificationChannel(@Value("${WHATSAPP_ENABLED:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String channelCode() {
        return "WHATSAPP";
    }

    @Override
    public void send(User user, String title, String message) {
        if (!enabled) {
            return;
        }
        if (user == null || user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            log.debug("Skipping WhatsApp — no phone for userId={}", user == null ? null : user.getId());
            return;
        }
        // Minimal stub: log intent; real Meta Cloud API integration deferred until credentials exist.
        log.info("WhatsApp notification queued userId={} phone={} title={}",
                user.getId(), maskPhone(user.getPhoneNumber()), title);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
