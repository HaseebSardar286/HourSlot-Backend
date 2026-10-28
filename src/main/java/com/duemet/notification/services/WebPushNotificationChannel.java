package com.duemet.notification.services;

import com.duemet.identity.model.User;
import org.springframework.stereotype.Component;

/** Mirrors every in-app notification to the user's phone / desktop as a push notification. */
@Component
public class WebPushNotificationChannel implements NotificationChannel {

    private final WebPushService webPushService;

    public WebPushNotificationChannel(WebPushService webPushService) {
        this.webPushService = webPushService;
    }

    @Override
    public String channelCode() {
        return "WEB_PUSH";
    }

    @Override
    public void send(User user, String title, String message) {
        // The app routes "/" to the right home page (customer vs provider) after sign-in.
        webPushService.sendToUser(user.getId(), title, message, "/");
    }
}
