package com.duemet.booking;

import com.duemet.availability.services.ScheduleService;
import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingItem;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.services.BookingService;
import com.duemet.catalog.model.Service;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.identity.model.CustomerProfile;
import com.duemet.identity.model.User;
import com.duemet.identity.model.UserRole;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.identity.repository.UserRepository;
import com.duemet.organization.model.Branch;
import com.duemet.organization.model.Business;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceGuardsTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private CustomerProfileRepository customerProfileRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private ServiceRepository serviceRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private ScheduleService scheduleService;
    @Mock private com.duemet.identity.repository.CustomerAddressRepository customerAddressRepository;
    @Mock private com.duemet.booking.repository.BookingVisitRepository bookingVisitRepository;
    @Mock private com.duemet.booking.services.BookingPolicyService bookingPolicyService;

    @InjectMocks private BookingService bookingService;

    private Branch branch;
    private Service service;
    private Staff staff;

    @BeforeEach
    void setUp() {
        User customer = User.builder().id(1L).build();
        Business business = Business.builder().id(2L).status(BusinessStatus.APPROVED).publishStatus("PUBLISHED").build();
        branch = Branch.builder().id(3L).business(business).build();
        service = Service.builder().id(4L).durationMinutes(60).build();
        staff = Staff.builder().id(5L).branch(branch).active(true).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerProfileRepository.findById(1L)).thenReturn(Optional.of(CustomerProfile.builder().build()));
        when(branchRepository.findById(3L)).thenReturn(Optional.of(branch));
        when(businessRepository.findById(2L)).thenReturn(Optional.of(business));
        when(serviceRepository.findById(4L)).thenReturn(Optional.of(service));
        when(staffRepository.findById(5L)).thenReturn(Optional.of(staff));
    }

    @Test
    void unknownPaymentMethodIsRejected() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "CASH"));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void clientCannotClaimPackagePaymentWithoutAPackage() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "PACKAGE"));
    }

    @Test
    void pastSlotIsRejected() {
        LocalDateTime past = LocalDateTime.now().minusMinutes(5);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, past, null, null, "VENUE"));
        verify(bookingRepository, never()).lockSlotScope(anyString());
    }

    @Test
    void staffLockIsTakenBeforeSlotValidation() {
        when(scheduleService.resolveDayHours(any(), any(), anyInt())).thenReturn(Optional.empty());
        LocalDateTime future = LocalDateTime.now().plusDays(1);

        assertThrows(RuntimeException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "ONLINE"));

        InOrder order = inOrder(bookingRepository, scheduleService);
        order.verify(bookingRepository).lockSlotScope("booking:staff:5");
        order.verify(scheduleService).resolveDayHours(any(), any(), anyInt());
    }

    @Test
    void cancelledBookingCannotBeRescheduledBackToActive() {
        Booking booking = Booking.builder()
                .id(10L)
                .branch(branch)
                .status(BookingStatus.CANCELLED)
                .items(new ArrayList<>(List.of(BookingItem.builder().service(service).staff(staff).build())))
                .build();
        when(bookingRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> bookingService.rescheduleBooking(10L, LocalDateTime.now().plusDays(2), UserRole.SUPER_ADMIN, 99L));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void urgentFeeIsPercentageOfPrice() {
        Service svc = Service.builder().urgentSurchargePercent(new java.math.BigDecimal("15")).build();
        assertEquals(new java.math.BigDecimal("225.00"), BookingService.urgentFee(svc, new java.math.BigDecimal("1500")));
        assertEquals(java.math.BigDecimal.ZERO, BookingService.urgentFee(Service.builder().build(), java.math.BigDecimal.TEN));
    }

    @Test
    void urgentBookingNeedsProviderAvailableNow() {
        LocalDateTime soon = LocalDateTime.now().plusMinutes(30);
        assertThrows(com.duemet.shared.exception.BookingConflictException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, soon, null, null, "VENUE",
                        new BookingService.VisitRequest(null, true, true)));
    }

    @Test
    void homeOnlyServiceRequiresAddressFromCustomer() {
        service.setServiceMode("CUSTOMER_LOCATION");
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "VENUE",
                        new BookingService.VisitRequest(null, false, true)));
    }

    @Test
    void addressIsRejectedForShopOnlyService() {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "VENUE",
                        new BookingService.VisitRequest(11L, false, true)));
    }

    @Test
    void someoneElsesAddressIsRejected() {
        service.setServiceMode("CUSTOMER_LOCATION");
        when(customerAddressRepository.findByIdAndUser(eq(11L), any())).thenReturn(Optional.empty());
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        assertThrows(IllegalArgumentException.class,
                () -> bookingService.createBooking(1L, 3L, 4L, 5L, future, null, null, "VENUE",
                        new BookingService.VisitRequest(11L, false, true)));
    }
}
