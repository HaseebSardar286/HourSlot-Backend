package com.hourslot.notification.services;

import com.hourslot.identity.model.User;

public interface NotificationChannel {
    String channelCode();

    void send(User user, String title, String message);
}
