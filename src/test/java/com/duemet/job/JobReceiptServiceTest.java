package com.duemet.job;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingVisit;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.job.model.Job;
import com.duemet.job.model.JobMaterial;
import com.duemet.job.repository.JobMaterialRepository;
import com.duemet.job.services.JobReceiptService;
import com.duemet.job.services.JobService;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobReceiptServiceTest {

    private final JobService jobService = mock(JobService.class);
    private final JobMaterialRepository materials = mock(JobMaterialRepository.class);
    private final BusinessRepository businesses = mock(BusinessRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingVisitRepository visits = mock(BookingVisitRepository.class);
    private final JobReceiptService receipts = new JobReceiptService(jobService, materials, businesses, users, bookings, visits);

    @Test
    void rendersPdfForCompletedJob() {
        Job job = Job.builder().id(1L).bookingId(9L).providerId(7L).customerId(3L).title("Fix wiring")
                .currency("PKR").status("COMPLETED").finalAmount(new BigDecimal("2675.00"))
                .completedAt(LocalDateTime.now()).build();
        when(jobService.requireAccessible(any(), eq(1L), any())).thenReturn(job);
        when(businesses.findById(7L)).thenReturn(Optional.of(Business.builder().id(7L).name("Ali Electric").build()));
        when(users.findById(3L)).thenReturn(Optional.of(User.builder().id(3L).firstName("Sara").build()));
        when(bookings.findById(9L)).thenReturn(Optional.of(
                Booking.builder().id(9L).publicCode("HS00000009").paymentStatus("PAID").paymentMethod("CASH").build()));
        when(visits.findByBookingId(9L)).thenReturn(Optional.of(BookingVisit.builder()
                .bookingId(9L).addressText("House 12, F-7, Islamabad").urgentFee(new BigDecimal("225.00")).build()));
        when(materials.findByJob(1L)).thenReturn(List.of(JobMaterial.builder()
                .name("Cable 10m").quantity(new BigDecimal("2")).unitPrice(new BigDecimal("225")).build()));

        byte[] pdf = receipts.render(1L, User.builder().id(3L).build(), null);

        assertTrue(pdf.length > 500);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void noReceiptBeforeCompletion() {
        when(jobService.requireAccessible(any(), eq(1L), any()))
                .thenReturn(Job.builder().id(1L).status("IN_PROGRESS").build());
        assertThrows(IllegalStateException.class, () -> receipts.render(1L, User.builder().id(3L).build(), null));
    }
}
