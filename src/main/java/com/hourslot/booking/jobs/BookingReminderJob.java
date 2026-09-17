package com.hourslot.booking.jobs;

import com.hourslot.booking.model.Booking;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.identity.model.User;
import com.hourslot.notification.services.NotificationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class BookingReminderJob {
    private static final Logger log = LogManager.getLogger(BookingReminderJob.class);

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    public BookingReminderJob(BookingRepository bookingRepository, NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${hourslot.reminders.cron:0 */15 * * * *}")
    public void sendReminders() {
        LocalDateTime now = LocalDateTime.now();
        remindWindow("REMINDER_24H", now.plusHours(23).plusMinutes(45), now.plusHours(24).plusMinutes(15),
                "Appointment tomorrow", "You have a confirmed appointment within 24 hours.");
        remindWindow("REMINDER_2H", now.plusHours(1).plusMinutes(45), now.plusHours(2).plusMinutes(15),
                "Appointment soon", "Your appointment starts in about 2 hours.");
    }

    private void remindWindow(String eventType, LocalDateTime from, LocalDateTime to, String title, String message) {
        List<Booking> bookings = bookingRepository.findConfirmedBetween(from, to);
        for (Booking booking : bookings) {
            User customer = booking.getCustomerUser();
            if (customer == null || customer.getId() == null) {
                continue;
            }
            String code = booking.getPublicCode() == null ? "#" + booking.getId() : booking.getPublicCode();
            notificationService.notify(customer, eventType, title, message + " (" + code + ")", booking.getId());
        }
        if (!bookings.isEmpty()) {
            log.info("Sent {} reminders for {}", bookings.size(), eventType);
        }
    }
}
