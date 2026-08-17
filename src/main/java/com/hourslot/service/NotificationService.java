package com.hourslot.service;

import com.hourslot.model.Notification;
import com.hourslot.model.User;
import com.hourslot.repository.NotificationRepository;
import com.hourslot.repository.UserRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LogManager.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notify(User user, String title, String message) {
        if (user == null || user.getId() == null) {
            log.debug("Skipping notification — user is null (title={})", title);
            return;
        }

        // Always attach a managed reference to avoid "detached entity passed to persist"
        User managedUser = userRepository.getReferenceById(user.getId());

        notificationRepository.save(Notification.builder()
                .user(managedUser)
                .title(title)
                .body(message)
                .channel("IN_APP")
                .read(false)
                .build());
        log.info("Notification created for userId={} title={}", user.getId(), title);
    }
}
