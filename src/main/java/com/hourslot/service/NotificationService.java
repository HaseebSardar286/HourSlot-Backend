package com.hourslot.service;

import com.hourslot.model.Notification;
import com.hourslot.model.User;
import com.hourslot.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    public void notify(User user, String title, String message) {
        if (user == null) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .read(false)
                .build());
    }
}
