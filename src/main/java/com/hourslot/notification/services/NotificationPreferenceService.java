package com.hourslot.notification.services;

import com.hourslot.notification.model.NotificationPreference;
import com.hourslot.identity.model.User;
import com.hourslot.notification.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getPreferences(User user) {
        List<NotificationPreference> prefs = notificationPreferenceRepository.findByUser(user);
        if (prefs.isEmpty()) {
            return defaultView();
        }
        Map<String, Boolean> view = defaultView();
        for (NotificationPreference pref : prefs) {
            String key = keyFor(pref.getChannel(), pref.getEventType());
            if (key != null) {
                view.put(key, pref.isEnabled());
            }
        }
        return view;
    }

    @Transactional
    public Map<String, Boolean> updatePreferences(User user, Map<String, Boolean> updates) {
        if (updates.containsKey("emailBooking")) {
            notificationPreferenceRepository.upsert(user, "EMAIL", "BOOKING", Boolean.TRUE.equals(updates.get("emailBooking")));
        }
        if (updates.containsKey("smsReminder")) {
            notificationPreferenceRepository.upsert(user, "SMS", "REMINDER", Boolean.TRUE.equals(updates.get("smsReminder")));
        }
        if (updates.containsKey("emailMarketing")) {
            notificationPreferenceRepository.upsert(user, "EMAIL", "MARKETING", Boolean.TRUE.equals(updates.get("emailMarketing")));
        }
        if (updates.containsKey("inAppMarketplace")) {
            notificationPreferenceRepository.upsert(user, "IN_APP", "MARKETPLACE", Boolean.TRUE.equals(updates.get("inAppMarketplace")));
        }
        return getPreferences(user);
    }

    @Transactional
    public void ensureDefaults(User user) {
        if (notificationPreferenceRepository.findByUser(user).isEmpty()) {
            notificationPreferenceRepository.seedDefaults(user);
        }
    }

    private static Map<String, Boolean> defaultView() {
        Map<String, Boolean> defaults = new HashMap<>();
        defaults.put("emailBooking", true);
        defaults.put("smsReminder", true);
        defaults.put("emailMarketing", false);
        defaults.put("inAppMarketplace", true);
        return defaults;
    }

    private static String keyFor(String channel, String eventType) {
        if ("EMAIL".equals(channel) && "BOOKING".equals(eventType)) {
            return "emailBooking";
        }
        if ("SMS".equals(channel) && "REMINDER".equals(eventType)) {
            return "smsReminder";
        }
        if ("EMAIL".equals(channel) && "MARKETING".equals(eventType)) {
            return "emailMarketing";
        }
        if ("IN_APP".equals(channel) && "MARKETPLACE".equals(eventType)) {
            return "inAppMarketplace";
        }
        return null;
    }
}
