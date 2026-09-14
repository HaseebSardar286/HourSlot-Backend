package com.hourslot.notification.services;

import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LogManager.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${app.frontend-base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public void sendEmail(String to, String subject, String contentHtml) {
        if (mailSender == null) {
            log.warn("Mail not configured — logging email instead. to={}, subject={}", to, subject);
            log.debug("Email body: {}", contentHtml);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (mailFrom != null && !mailFrom.isBlank()) {
                helper.setFrom(mailFrom.trim());
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(contentHtml, true);
            mailSender.send(message);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage(), e);
        }
    }

    public void sendPasswordResetEmail(String email, String token) {
        String resetUrl = frontendBaseUrl + "/auth/reset-password?token=" + token;
        String subject = "Reset Your Password - HourSlot";
        String html = "<h2>Password Reset Request</h2>"
                + "<p>Click the link below to set a new password:</p>"
                + "<p><a href=\"" + resetUrl + "\" style=\"display:inline-block;padding:10px 20px;"
                + "color:#fff;background-color:#1a8a8a;border-radius:4px;text-decoration:none;\">Reset Password</a></p>"
                + "<p>If you did not request this, you can ignore this email.</p>";
        sendEmail(email, subject, html);
    }

    public void sendBookingCreatedEmail(
            String email, String customerName, String serviceName, String timeStr, String branchName) {
        String subject = "Booking Confirmed - HourSlot";
        String html = "<h2>Hello " + customerName + "!</h2>"
                + "<p>Your booking for <strong>" + serviceName + "</strong> at <strong>"
                + branchName + "</strong> is confirmed.</p>"
                + "<p><strong>Appointment time:</strong> " + timeStr + "</p>"
                + "<p>Thank you for booking with HourSlot.</p>";
        sendEmail(email, subject, html);
    }

    public void sendBookingStatusEmail(
            String email, String customerName, String serviceName, String timeStr, String status) {
        String subject = "Booking Status Update - HourSlot";
        String html = "<h2>Hello " + customerName + "!</h2>"
                + "<p>Your booking for <strong>" + serviceName + "</strong> on " + timeStr
                + " is now <strong>" + status + "</strong>.</p>";
        sendEmail(email, subject, html);
    }

    public void sendPackagePurchaseEmail(
            String email, String customerName, String packageName, double price, int sessions) {
        String subject = "Package Purchase Confirmed - HourSlot";
        String html = "<h2>Package Purchased</h2>"
                + "<p>Hello " + customerName + ", your package <strong>" + packageName + "</strong> is active.</p>"
                + "<ul><li>Price: $" + price + "</li><li>Sessions: " + sessions + "</li></ul>";
        sendEmail(email, subject, html);
    }

    public void sendStaffInviteEmail(
            String email, String displayName, String businessName, String branchName, String acceptUrl) {
        String subject = "You're invited to join " + businessName + " on HourSlot";
        String html = "<h2>Staff invitation</h2>"
                + "<p>Hello " + displayName + ",</p>"
                + "<p>You have been invited to join <strong>" + businessName + "</strong>"
                + (branchName != null && !branchName.isBlank() ? " at <strong>" + branchName + "</strong>" : "")
                + ".</p>"
                + "<p><a href=\"" + acceptUrl + "\" style=\"display:inline-block;padding:10px 20px;"
                + "color:#fff;background-color:#1a8a8a;border-radius:4px;text-decoration:none;\">Accept invitation</a></p>"
                + "<p>This link expires in 7 days.</p>";
        sendEmail(email, subject, html);
    }

    public void sendEmailVerificationEmail(String email, String firstName, String token) {
        String verifyUrl = frontendBaseUrl + "/auth/verify-email?token=" + token;
        String subject = "Verify your email - HourSlot";
        String html = "<h2>Welcome" + (firstName != null && !firstName.isBlank() ? ", " + firstName : "") + "!</h2>"
                + "<p>Please verify your email address to complete your HourSlot account setup.</p>"
                + "<p><a href=\"" + verifyUrl + "\" style=\"display:inline-block;padding:10px 20px;"
                + "color:#fff;background-color:#1a8a8a;border-radius:4px;text-decoration:none;\">Verify email</a></p>"
                + "<p>If you did not create an account, you can ignore this email.</p>";
        sendEmail(email, subject, html);
    }

    public void sendBookingCancelledEmail(
            String email, String customerName, String serviceName, String timeStr, String branchName) {
        String subject = "Booking Cancelled - HourSlot";
        String html = "<h2>Hello " + customerName + "!</h2>"
                + "<p>Your booking for <strong>" + serviceName + "</strong> at <strong>"
                + branchName + "</strong> on " + timeStr + " has been cancelled.</p>"
                + "<p>If this was a mistake, you can book again from HourSlot.</p>";
        sendEmail(email, subject, html);
    }
}
