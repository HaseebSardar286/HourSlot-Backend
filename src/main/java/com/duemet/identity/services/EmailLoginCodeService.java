package com.duemet.identity.services;

import com.duemet.identity.model.CustomerProfile;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.identity.repository.EmailLoginCodeRepository;
import com.duemet.identity.repository.UserRepository;
import com.duemet.notification.services.MailService;
import com.duemet.organization.services.RbacService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Passwordless sign-in with a 6-digit code sent to the user's email.
 * Verifying a code proves the address, so an unknown email gets a new customer account.
 */
@Service
public class EmailLoginCodeService {

    private static final Logger log = LogManager.getLogger(EmailLoginCodeService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    static final int CODE_TTL_MINUTES = 10;
    static final int MAX_ATTEMPTS = 5;
    static final int RESEND_COOLDOWN_SECONDS = 60;

    private final SecureRandom random = new SecureRandom();
    private final EmailLoginCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final RbacService rbacService;
    private final MailService mailService;
    private final PasswordEncoder passwordEncoder;
    private final byte[] hashKey;
    private final boolean logCodes;

    public EmailLoginCodeService(EmailLoginCodeRepository codeRepository,
                                 UserRepository userRepository,
                                 CustomerProfileRepository customerProfileRepository,
                                 RbacService rbacService,
                                 MailService mailService,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.security.jwt.secret}") String hashSecret,
                                 @Value("${app.otp.log-codes:false}") boolean logCodes) {
        this.codeRepository = codeRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.rbacService = rbacService;
        this.mailService = mailService;
        this.passwordEncoder = passwordEncoder;
        this.hashKey = hashSecret.getBytes(StandardCharsets.UTF_8);
        this.logCodes = logCodes;
    }

    /**
     * Send a sign-in code. Behaves the same whether or not the email has an account,
     * so the endpoint cannot be used to discover registered addresses.
     */
    @Transactional
    public void requestCode(String rawEmail) {
        String email = normalize(rawEmail);
        LocalDateTime last = codeRepository.lastIssuedAt(email).orElse(null);
        if (last != null && last.isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            throw new IllegalStateException("Please wait a minute before requesting another code.");
        }
        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null && existing.getStatus() != null && !"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
            log.info("Sign-in code not sent: account for {} is {}", email, existing.getStatus());
            return;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        codeRepository.issue(email, hash(email, code), LocalDateTime.now().plusMinutes(CODE_TTL_MINUTES));
        if (logCodes) {
            log.info("DEV sign-in code for {}: {}", email, code);
        }
        mailService.sendLoginCodeEmail(email, code, CODE_TTL_MINUTES);
    }

    /** Check the code and return the signed-in user, creating a customer account for a new email. */
    @Transactional
    public User verify(String rawEmail, String rawCode) {
        String email = normalize(rawEmail);
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        EmailLoginCodeRepository.ActiveCode active = codeRepository.findActive(email)
                .orElseThrow(EmailLoginCodeService::invalid);
        if (active.expiresAt().isBefore(LocalDateTime.now()) || active.attempts() >= MAX_ATTEMPTS) {
            codeRepository.consume(active.id());
            throw invalid();
        }
        if (!MessageDigest.isEqual(active.codeHash().getBytes(StandardCharsets.UTF_8),
                hash(email, code).getBytes(StandardCharsets.UTF_8))) {
            codeRepository.recordFailedAttempt(active.id());
            throw invalid();
        }
        if (!codeRepository.consume(active.id())) {
            throw invalid();
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return createCustomer(email);
        }
        if (user.getStatus() != null && !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new SecurityException("This account is not active.");
        }
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(LocalDateTime.now());
            userRepository.save(user);
        }
        return user;
    }

    private User createCustomer(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                // No password: the account signs in with email codes until the user sets one via "forgot password".
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .status("ACTIVE")
                .providerIntent("CUSTOMER")
                .emailVerifiedAt(LocalDateTime.now())
                .build());
        customerProfileRepository.save(CustomerProfile.builder().user(user).build());
        rbacService.grantSystemRole(user, "CUSTOMER", null, null, null, null);
        log.info("Created customer account via email code for userId={}", user.getId());
        return user;
    }

    private static String normalize(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        return email;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("That code is invalid or has expired. Request a new one.");
    }

    /** HMAC so a leaked table cannot be brute-forced offline without the server secret. */
    String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash sign-in code", ex);
        }
    }
}
