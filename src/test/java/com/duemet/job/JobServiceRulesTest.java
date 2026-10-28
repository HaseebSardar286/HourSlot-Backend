package com.duemet.job;

import com.duemet.booking.model.Booking;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.job.model.Job;
import com.duemet.job.repository.DisputeRepository;
import com.duemet.job.repository.JobMaterialRepository;
import com.duemet.job.repository.JobMediaRepository;
import com.duemet.job.repository.JobNoteRepository;
import com.duemet.job.repository.JobRepository;
import com.duemet.job.repository.JobStatusHistoryRepository;
import com.duemet.job.services.JobService;
import com.duemet.notification.services.NotificationService;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.services.AuditService;
import com.duemet.payment.model.Payment;
import com.duemet.payment.repository.PaymentRepository;
import com.duemet.request.repository.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceRulesTest {

    private static final long PROVIDER_ID = 7L;
    private static final long CUSTOMER_ID = 3L;

    @Mock private JobRepository jobRepository;
    @Mock private JobStatusHistoryRepository historyRepository;
    @Mock private JobMediaRepository mediaRepository;
    @Mock private JobNoteRepository noteRepository;
    @Mock private DisputeRepository disputeRepository;
    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private AuditService auditService;
    @Mock private BusinessRepository businessRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private JobMaterialRepository materialRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BookingVisitRepository bookingVisitRepository;

    @InjectMocks private JobService jobService;

    private final User customer = User.builder().id(CUSTOMER_ID).build();
    private final User providerUser = User.builder().id(50L).build();
    private Job job;

    @BeforeEach
    void setUp() {
        job = Job.builder().id(1L).bookingId(9L).providerId(PROVIDER_ID).customerId(CUSTOMER_ID)
                .title("Fix wiring").currency("PKR").estimatedAmount(new BigDecimal("2000")).status("SCHEDULED").build();
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void customerCannotAdvanceJobProgress() {
        job.setStatus("EN_ROUTE");
        assertThrows(SecurityException.class,
                () -> jobService.transition(1L, "ARRIVED", customer, null, null, null));
    }

    @Test
    void customerCanCancelBeforeProviderIsOnTheWay() {
        Job saved = jobService.transition(1L, "CANCELLED", customer, null, "changed plans", null);
        assertEquals("CANCELLED", saved.getStatus());
    }

    @Test
    void customerCannotCancelOnceProviderIsOnTheWay() {
        job.setStatus("EN_ROUTE");
        assertThrows(SecurityException.class,
                () -> jobService.transition(1L, "CANCELLED", customer, null, null, null));
    }

    @Test
    void completingWithoutAmountBillsLabourPlusMaterials() {
        job.setStatus("IN_PROGRESS");
        when(materialRepository.totalForJob(1L)).thenReturn(new BigDecimal("450.00"));
        Job saved = jobService.transition(1L, "COMPLETED", providerUser, PROVIDER_ID, null, null);
        assertEquals(new BigDecimal("2450.00"), saved.getFinalAmount());
    }

    @Test
    void collectingCashMarksBookingPaidAndRecordsPayment() {
        job.setStatus("COMPLETED");
        job.setFinalAmount(new BigDecimal("2450.00"));
        Booking booking = Booking.builder().id(9L).paymentStatus("UNPAID").paymentMethod("VENUE").build();
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(booking));

        jobService.collectPayment(1L, providerUser, PROVIDER_ID, null, "cash");

        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals("CASH", payment.getValue().getProvider());
        assertEquals(new BigDecimal("2450.00"), payment.getValue().getAmount());
        assertEquals("PAID", booking.getPaymentStatus());
        assertEquals("CASH", booking.getPaymentMethod());
    }

    @Test
    void paymentCannotBeRecordedTwice() {
        job.setStatus("COMPLETED");
        when(bookingRepository.findById(9L)).thenReturn(Optional.of(
                Booking.builder().id(9L).paymentStatus("PAID").build()));
        assertThrows(IllegalStateException.class,
                () -> jobService.collectPayment(1L, providerUser, PROVIDER_ID, null, "CASH"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void onlyOfflineMethodsCanBeRecorded() {
        job.setStatus("COMPLETED");
        assertThrows(IllegalArgumentException.class,
                () -> jobService.collectPayment(1L, providerUser, PROVIDER_ID, null, "STRIPE"));
    }

    @Test
    void customerCannotRecordPayment() {
        job.setStatus("COMPLETED");
        assertThrows(SecurityException.class,
                () -> jobService.collectPayment(1L, customer, null, null, "CASH"));
    }

    @Test
    void onlyCustomerConfirmsCompletion() {
        job.setStatus("COMPLETED");
        assertThrows(SecurityException.class, () -> jobService.confirmCompletion(1L, providerUser));
        Job confirmed = jobService.confirmCompletion(1L, customer);
        assertNotNull(confirmed.getCustomerConfirmedAt());
    }

    @Test
    void materialsAreProviderOnly() {
        job.setStatus("IN_PROGRESS");
        assertThrows(SecurityException.class, () -> jobService.addMaterial(1L, customer, null,
                "Cable", BigDecimal.ONE, BigDecimal.TEN));
    }
}
