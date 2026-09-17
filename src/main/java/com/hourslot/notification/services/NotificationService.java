package com.hourslot.notification.services;

import com.hourslot.identity.model.User;
import com.hourslot.identity.repository.UserRepository;
import com.hourslot.notification.model.Notification;
import com.hourslot.notification.repository.NotificationRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannel> channels;
    private final NotificationPreferenceService preferenceService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            List<NotificationChannel> channels,
            NotificationPreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.channels = channels;
        this.preferenceService = preferenceService;
    }

    @Transactional
    public void notify(User user, String title, String message) {
        notify(user, null, title, message, null);
    }

    @Transactional
    public void notify(User user, String eventType, String title, String message, Long referenceId) {
        if (user == null || user.getId() == null) {
            log.debug("Skipping notification — user is null (title={})", title);
            return;
        }
        if (eventType != null && referenceId != null
                && notificationRepository.existsEvent(user.getId(), eventType, referenceId)) {
            return;
        }

        Map<String, Boolean> prefs = preferenceService.getPreferences(user);
        if (isReminder(eventType) && !Boolean.TRUE.equals(prefs.get("smsReminder"))
                && !Boolean.TRUE.equals(prefs.get("inAppMarketplace"))) {
            // still keep in-app reminders unless marketplace in-app is explicitly off
        }
        if (Boolean.FALSE.equals(prefs.get("inAppMarketplace")) && isMarketplace(eventType)) {
            return;
        }

        User managedUser = userRepository.getReferenceById(user.getId());
        notificationRepository.save(Notification.builder()
                .user(managedUser)
                .title(title)
                .body(message)
                .channel("IN_APP")
                .eventType(eventType)
                .referenceId(referenceId)
                .read(false)
                .build());
        log.info("Notification created for userId={} event={} title={}", user.getId(), eventType, title);

        for (NotificationChannel channel : channels) {
            if (!"WHATSAPP".equalsIgnoreCase(channel.channelCode())) {
                continue;
            }
            try {
                channel.send(user, title, message);
            } catch (Exception ex) {
                log.warn("WhatsApp notification failed for userId={}: {}", user.getId(), ex.getMessage());
            }
        }
    }

    private static boolean isReminder(String eventType) {
        return eventType != null && eventType.startsWith("REMINDER");
    }

    private static boolean isMarketplace(String eventType) {
        if (eventType == null) {
            return false;
        }
        return eventType.startsWith("REQUEST")
                || eventType.startsWith("QUOTE")
                || eventType.startsWith("JOB")
                || eventType.startsWith("BOOKING")
                || eventType.startsWith("REMINDER");
    }
}
