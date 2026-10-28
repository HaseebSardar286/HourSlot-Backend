package com.duemet.identity;

import com.duemet.identity.model.User;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.identity.repository.EmailLoginCodeRepository;
import com.duemet.identity.repository.EmailLoginCodeRepository.ActiveCode;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.services.EmailLoginCodeService;
import com.duemet.notification.services.MailService;
import com.duemet.organization.services.RbacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailLoginCodeServiceTest {

    private final EmailLoginCodeRepository codes = mock(EmailLoginCodeRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
    private final RbacService rbac = mock(RbacService.class);
    private final MailService mail = mock(MailService.class);
    private EmailLoginCodeService service;

    @BeforeEach
    void setUp() {
        service = new EmailLoginCodeService(codes, users, profiles, rbac, mail,
                new BCryptPasswordEncoder(4), "test-secret", false);
        when(users.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(42L);
            return u;
        });
        when(codes.consume(anyLong())).thenReturn(true);
    }

    /** Request a code and capture what was emailed and stored. */
    private String requestAndCapture(String email) {
        service.requestCode(email);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail).sendLoginCodeEmail(eq(email.toLowerCase()), code.capture(), anyInt());
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(codes).issue(eq(email.toLowerCase()), hash.capture(), any());
        when(codes.findActive(email.toLowerCase())).thenReturn(Optional.of(
                new ActiveCode(1L, hash.getValue(), 0, LocalDateTime.now().plusMinutes(10), LocalDateTime.now())));
        return code.getValue();
    }

    @Test
    void codeIsSixDigitsAndOnlyItsHashIsStored() {
        String code = requestAndCapture("Ali@Example.com");
        assertTrue(code.matches("\\d{6}"));
        verify(codes).issue(eq("ali@example.com"), argThat(h -> !h.contains(code) && h.length() == 64), any());
    }

    @Test
    void correctCodeSignsInExistingUserAndVerifiesEmail() {
        User existing = User.builder().id(7L).email("ali@example.com").status("ACTIVE").build();
        when(users.findByEmail("ali@example.com")).thenReturn(Optional.of(existing));
        String code = requestAndCapture("ali@example.com");

        User signedIn = service.verify("ali@example.com", code);

        assertEquals(7L, signedIn.getId());
        assertNotNull(signedIn.getEmailVerifiedAt());
        verify(codes).consume(1L);
    }

    @Test
    void newEmailGetsVerifiedCustomerAccount() {
        when(users.findByEmail("new@example.com")).thenReturn(Optional.empty());
        String code = requestAndCapture("new@example.com");

        User created = service.verify("new@example.com", code);

        assertEquals("new@example.com", created.getEmail());
        assertNotNull(created.getEmailVerifiedAt());
        verify(rbac).grantSystemRole(any(), eq("CUSTOMER"), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void wrongCodeCountsAnAttempt() {
        requestAndCapture("ali@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.verify("ali@example.com", "000000x"));
        verify(codes).recordFailedAttempt(1L);
        verify(codes, never()).consume(anyLong());
    }

    @Test
    void lockedAfterTooManyAttempts() {
        String code = requestAndCapture("ali@example.com");
        when(codes.findActive("ali@example.com")).thenReturn(Optional.of(
                new ActiveCode(1L, "irrelevant", 5, LocalDateTime.now().plusMinutes(10), LocalDateTime.now())));
        assertThrows(IllegalArgumentException.class, () -> service.verify("ali@example.com", code));
    }

    @Test
    void expiredCodeIsRejected() {
        when(codes.findActive("ali@example.com")).thenReturn(Optional.of(
                new ActiveCode(1L, "x", 0, LocalDateTime.now().minusSeconds(1), LocalDateTime.now().minusMinutes(11))));
        assertThrows(IllegalArgumentException.class, () -> service.verify("ali@example.com", "123456"));
    }

    @Test
    void resendIsThrottled() {
        when(codes.lastIssuedAt("ali@example.com")).thenReturn(Optional.of(LocalDateTime.now().minusSeconds(10)));
        assertThrows(IllegalStateException.class, () -> service.requestCode("ali@example.com"));
        verify(mail, never()).sendLoginCodeEmail(any(), any(), anyInt());
    }

    @Test
    void suspendedAccountGetsNoCodeButSameResponse() {
        when(users.findByEmail("ali@example.com")).thenReturn(Optional.of(
                User.builder().id(7L).email("ali@example.com").status("SUSPENDED").build()));
        assertDoesNotThrow(() -> service.requestCode("ali@example.com"));
        verify(mail, never()).sendLoginCodeEmail(any(), any(), anyInt());
    }

    @Test
    void invalidEmailIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.requestCode("not-an-email"));
    }
}
