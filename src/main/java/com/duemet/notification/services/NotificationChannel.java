package com.duemet.notification.services;

import com.duemet.identity.model.User;

public interface NotificationChannel {
    String channelCode();

    void send(User user, String title, String message);
}
