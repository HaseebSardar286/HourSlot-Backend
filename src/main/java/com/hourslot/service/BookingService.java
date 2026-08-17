package com.hourslot.service;

import com.hourslot.model.*;
import com.hourslot.repository.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LogManager.getLogger(BookingService.class);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private TimeOfDayPricingRepository timeOfDayPricingRepository;

    @Autowired
    private SlotLockService slotLockService;

    @Autowired
    private CustomerPackageRepository customerPackageRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId, LocalDateTime bookingTime) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime, null, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime, clientNotes, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes, Long customerPackageId) {
        log.info("Creating booking customerId={} branchId={} serviceId={} staffId={} time={} packageId={}",
                customerId, branchId, serviceId, staffId, bookingTime, customerPackageId);
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found."));
        customerProfileRepository.findById(customerId)
                .orElseGet(() -> customerProfileRepository.save(CustomerProfile.builder().user(customer).build()));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));
        com.hourslot.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found."));

        Business business = branch.getBusiness();
        if (business.getStatus() != BusinessStatus.APPROVED || !business.isVerified()) {
            throw new RuntimeException("This business is not currently accepting bookings.");
        }

        Staff staff = null;
        if (staffId != null) {
            staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                throw new RuntimeException("Staff does not belong to the selected branch.");
            }
        }

        String lockKey = slotLockService.buildLockKey(branchId, staffId, serviceId, bookingTime);
        if (!slotLockService.tryAcquire(lockKey)) {
            throw new RuntimeException("This slot is temporarily held by another customer. Please try again.");
        }

        try {
            return createBookingInternal(customer, branch, service, staff, bookingTime, clientNotes, lockKey, customerPackageId);
        } catch (RuntimeException ex) {
            slotLockService.release(lockKey);
            throw ex;
        }
    }

    private Booking createBookingInternal(User customer, Branch branch, com.hourslot.model.Service service,
                                          Staff staff, LocalDateTime bookingTime, String clientNotes, String lockKey, Long customerPackageId) {
        LocalDateTime endTime = bookingTime.plusMinutes(service.getDurationMinutes());
        validateSlot(branch, staff, service, bookingTime, endTime, null);

        BigDecimal basePrice = service.getBasePrice() == null ? BigDecimal.ZERO : service.getBasePrice();
        if (staff != null) {
            StaffService mapping = staffServiceRepository.findByStaffAndService(staff, service).orElse(null);
            if (mapping != null && mapping.getPriceOverride() != null) {
                basePrice = BigDecimal.valueOf(mapping.getPriceOverride());
            }
        }

        DayOfWeek day = bookingTime.getDayOfWeek();
        int dayNum = day.getValue();
        LocalTime startLocal = bookingTime.toLocalTime();
        LocalTime endLocal = endTime.toLocalTime();

        BigDecimal multiplier = BigDecimal.ONE;
        List<TimeOfDayPricing> peakRules = timeOfDayPricingRepository.findByServiceAndDayOfWeek(service, dayNum);
        for (TimeOfDayPricing rule : peakRules) {
            if (!rule.isActive()) {
                continue;
            }
            if (startLocal.isBefore(rule.getEndTime()) && endLocal.isAfter(rule.getStartTime())) {
                BigDecimal candidate = BigDecimal.valueOf(rule.getPriceMultiplier());
                if (candidate.compareTo(multiplier) > 0) {
                    multiplier = candidate;
                }
            }
        }

        BigDecimal finalPrice = basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        String paymentStatus = "UNPAID";
        String paymentMethod = "VENUE";
        CustomerPackage customerPackage = null;

        if (customerPackageId != null) {
            customerPackage = customerPackageRepository.findById(customerPackageId)
                    .orElseThrow(() -> new RuntimeException("Selected package was not found."));
            if (!customerPackage.getCustomerUser().getId().equals(customer.getId())) {
                throw new RuntimeException("Selected package does not belong to this customer.");
            }
            if (!customerPackage.getStatus().equals("ACTIVE") || customerPackage.getSessionsRemaining() <= 0) {
                throw new RuntimeException("Selected package is no longer active or is exhausted.");
            }
            if (customerPackage.getExpiresAt() != null && LocalDateTime.now().isAfter(customerPackage.getExpiresAt())) {
                customerPackage.setStatus("EXPIRED");
                customerPackageRepository.save(customerPackage);
                throw new RuntimeException("Selected package has expired.");
            }

            boolean isIncluded = customerPackage.getServicePackage().getServices().stream()
                    .anyMatch(s -> s.getId().equals(service.getId()));
            if (!isIncluded) {
                throw new RuntimeException("This service is not included in the selected package.");
            }

            customerPackage.setSessionsRemaining(customerPackage.getSessionsRemaining() - 1);
            if (customerPackage.getSessionsRemaining() <= 0) {
                customerPackage.setStatus("EXHAUSTED");
            }
            customerPackageRepository.save(customerPackage);
            paymentStatus = "PAID (Package)";
            paymentMethod = "PACKAGE";
        }

        Business business = branch.getBusiness();
        Booking booking = Booking.builder()
                .customerUser(customer)
                .organization(business.getOrganization())
                .business(business)
                .branch(branch)
                .bookingTime(bookingTime)
                .endTime(endTime)
                .totalPrice(finalPrice)
                .currency(service.getCurrency() == null ? "USD" : service.getCurrency())
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .source("MARKETPLACE")
                .customerPackage(customerPackage)
                .clientNotes(clientNotes)
                .items(new ArrayList<>())
                .build();

        BookingItem item = BookingItem.builder()
                .booking(booking)
                .service(service)
                .staff(staff)
                .startTime(bookingTime)
                .endTime(endTime)
                .unitPrice(basePrice)
                .priceMultiplier(multiplier)
                .lineTotal(finalPrice)
                .sortOrder(0)
                .build();
        booking.getItems().add(item);

        Booking saved = bookingRepository.save(booking);
        saved.setPublicCode("HS" + String.format("%08d", saved.getId()));
        saved = bookingRepository.save(saved);

        bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(saved)
                .fromStatus(null)
                .toStatus(saved.getStatus().name())
                .changedBy(customer)
                .reason("CREATED")
                .build());

        slotLockService.release(lockKey);
        log.info("Booking created id={} customerId={} status={} paymentStatus={}",
                saved.getId(), customer.getId(), saved.getStatus(), saved.getPaymentStatus());
        return saved;
    }

    private void validateSlot(Branch branch, Staff staff, com.hourslot.model.Service service,
                              LocalDateTime bookingTime, LocalDateTime endTime, Long excludeBookingId) {
        int dayNum = bookingTime.getDayOfWeek().getValue();
        ScheduleService.DayHours dayConfig = scheduleService.resolveDayHours(branch, staff, dayNum)
                .orElse(null);
        if (dayConfig == null || dayConfig.isClosed()) {
            throw new RuntimeException("The selected date/time is outside working hours (Closed).");
        }

        LocalTime startLocal = bookingTime.toLocalTime();
        LocalTime endLocal = endTime.toLocalTime();
        if (dayConfig.getStartTime() == null || dayConfig.getEndTime() == null ||
                startLocal.isBefore(dayConfig.getStartTime()) || endLocal.isAfter(dayConfig.getEndTime())) {
            throw new RuntimeException("The selected slot is outside working shifts.");
        }

        if (dayConfig.getBreaks() != null) {
            for (ScheduleService.TimeWindow br : dayConfig.getBreaks()) {
                if (startLocal.isBefore(br.getEndTime()) && endLocal.isAfter(br.getStartTime())) {
                    throw new RuntimeException("The selected slot overlaps with a scheduled break.");
                }
            }
        }

        if (scheduleService.isHoliday(branch, staff, bookingTime.toLocalDate())) {
            throw new RuntimeException("The selected date is scheduled as a holiday or absence.");
        }

        List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

        if (staff != null) {
            List<Booking> overlapping = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                    staff,
                    bookingTime.minusMinutes(service.getBufferMinutes()).minusMinutes(service.getDurationMinutes()),
                    endTime.plusMinutes(service.getBufferMinutes()),
                    activeStatuses
            );
            for (Booking ob : overlapping) {
                if (excludeBookingId != null && ob.getId().equals(excludeBookingId)) {
                    continue;
                }
                com.hourslot.model.Service obService = ob.getService();
                LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                LocalDateTime obEndEff = ob.getEndTime().plusMinutes(obService == null ? 0 : obService.getBufferMinutes());
                if (bookingTime.isBefore(obEndEff) && bEndEff.isAfter(ob.getBookingTime())) {
                    throw new RuntimeException("The selected staff member has an overlapping booking during this slot.");
                }
            }
        } else {
            List<Booking> branchOverlaps = bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(
                    branch,
                    bookingTime.minusMinutes(service.getDurationMinutes()),
                    endTime.plusMinutes(service.getBufferMinutes()),
                    activeStatuses
            );
            int concurrent = 0;
            for (Booking ob : branchOverlaps) {
                if (excludeBookingId != null && ob.getId().equals(excludeBookingId)) {
                    continue;
                }
                com.hourslot.model.Service obService = ob.getService();
                if (obService != null && !obService.getId().equals(service.getId()) && !service.isGroupService()) {
                    continue;
                }
                LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                int obBuffer = obService == null ? 0 : obService.getBufferMinutes();
                LocalDateTime obEndEff = ob.getEndTime().plusMinutes(obBuffer);
                if (bookingTime.isBefore(obEndEff) && bEndEff.isAfter(ob.getBookingTime())) {
                    concurrent++;
                }
            }
            int maxConcurrent = service.getMaxConcurrent() > 0 ? service.getMaxConcurrent()
                    : (service.getCapacity() > 0 ? service.getCapacity() : 1);
            if (concurrent >= maxConcurrent) {
                throw new RuntimeException("No capacity left for this service at the selected time.");
            }
        }
    }

    @Transactional
    public Booking rescheduleBooking(Long bookingId, LocalDateTime newTime, UserRole role, Long userId) {
        log.info("Reschedule requested bookingId={} newTime={} role={} userId={}", bookingId, role, userId, newTime);
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        boolean authorized = false;
        if (role == UserRole.SUPER_ADMIN) {
            authorized = true;
        } else if (role == UserRole.CUSTOMER && booking.getCustomer() != null && booking.getCustomer().getId().equals(userId)) {
            authorized = true;
        } else if (role == UserRole.BUSINESS_OWNER) {
            User owner = tenancyService.findOwner(booking.getBranch().getBusiness()).orElse(null);
            authorized = owner != null && owner.getId().equals(userId);
        } else if (role == UserRole.BUSINESS_STAFF) {
            Staff staff = booking.getStaff();
            authorized = staff != null && staff.getUser() != null && staff.getUser().getId().equals(userId);
        }

        if (!authorized) {
            throw new RuntimeException("Unauthorized to reschedule this booking.");
        }

        Branch branch = booking.getBranch();
        Staff staff = booking.getStaff();
        com.hourslot.model.Service service = booking.getService();
        if (service == null) {
            throw new RuntimeException("Booking is missing service data.");
        }

        String lockKey = slotLockService.buildLockKey(branch.getId(), staff != null ? staff.getId() : null, service.getId(), newTime);
        if (!slotLockService.tryAcquire(lockKey)) {
            throw new RuntimeException("This slot is temporarily held by another customer. Please try again.");
        }

        try {
            LocalDateTime endTime = newTime.plusMinutes(service.getDurationMinutes());
            validateSlot(branch, staff, service, newTime, endTime, bookingId);

            BookingStatus previous = booking.getStatus();
            booking.setBookingTime(newTime);
            booking.setEndTime(endTime);
            booking.setStatus(BookingStatus.CONFIRMED);
            if (booking.primaryItem() != null) {
                booking.primaryItem().setStartTime(newTime);
                booking.primaryItem().setEndTime(endTime);
            }

            Booking saved = bookingRepository.save(booking);
            bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                    .booking(saved)
                    .fromStatus(previous.name())
                    .toStatus(saved.getStatus().name())
                    .reason("RESCHEDULED")
                    .build());
            slotLockService.release(lockKey);
            return saved;
        } catch (RuntimeException ex) {
            slotLockService.release(lockKey);
            throw ex;
        }
    }
}
