package com.hourslot.service;

import com.hourslot.model.*;
import com.hourslot.repository.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LogManager.getLogger(BookingService.class);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private WorkingHourRepository workingHourRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private TimeOfDayPricingRepository timeOfDayPricingRepository;

    @Autowired
    private SlotLockService slotLockService;

    @Autowired
    private CustomerPackageRepository customerPackageRepository;

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
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found."));
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

    private Booking createBookingInternal(Customer customer, Branch branch, com.hourslot.model.Service service,
                                          Staff staff, LocalDateTime bookingTime, String clientNotes, String lockKey, Long customerPackageId) {
        LocalDateTime endTime = bookingTime.plusMinutes(service.getDurationMinutes());

        // 1. Basic Working Hours Validation
        DayOfWeek day = bookingTime.getDayOfWeek();
        int dayNum = day.getValue(); // 1 = Mon, 7 = Sun
        
        List<WorkingHour> whs;
        if (staff != null) {
            whs = workingHourRepository.findByStaffOrderByDayOfWeekAsc(staff);
            if (whs.isEmpty()) {
                whs = workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch);
            }
        } else {
            whs = workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch);
        }

        WorkingHour dayConfig = whs.stream()
                .filter(w -> w.getDayOfWeek() == dayNum)
                .findFirst()
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

        // 2. Break Periods Validation
        if (dayConfig.getBreaks() != null) {
            for (Break br : dayConfig.getBreaks()) {
                if (startLocal.isBefore(br.getEndTime()) && endLocal.isAfter(br.getStartTime())) {
                    throw new RuntimeException("The selected slot overlaps with a scheduled break.");
                }
            }
        }

        // 3. Holiday Closure Validation
        List<Holiday> hols;
        if (staff != null) {
            hols = holidayRepository.findByStaffAndDate(staff, bookingTime.toLocalDate());
            if (hols.isEmpty()) {
                hols = holidayRepository.findByBranchAndDate(branch, bookingTime.toLocalDate());
            }
        } else {
            hols = holidayRepository.findByBranchAndDate(branch, bookingTime.toLocalDate());
        }

        if (!hols.isEmpty()) {
            throw new RuntimeException("The selected date is scheduled as a holiday or absence.");
        }

        // 4. Concurrency & Overlapping Bookings Validation
        List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

        if (staff != null) {
            List<Booking> overlapping = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                    staff,
                    bookingTime.minusMinutes(service.getBufferMinutes()).minusMinutes(service.getDurationMinutes()),
                    endTime.plusMinutes(service.getBufferMinutes()),
                    activeStatuses
            );

            for (Booking ob : overlapping) {
                LocalDateTime obStart = ob.getBookingTime();
                LocalDateTime obEnd = ob.getEndTime();

                LocalDateTime bStartEff = bookingTime;
                LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                LocalDateTime obStartEff = obStart;
                LocalDateTime obEndEff = obEnd.plusMinutes(ob.getService().getBufferMinutes());

                if (bStartEff.isBefore(obEndEff) && bEndEff.isAfter(obStartEff)) {
                    throw new RuntimeException("The selected staff member has an overlapping booking during this slot.");
                }
            }
        } else {
            // Staff-less / capacity-aware overlap check
            List<Booking> branchOverlaps = bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(
                    branch,
                    bookingTime.minusMinutes(service.getDurationMinutes()),
                    endTime.plusMinutes(service.getBufferMinutes()),
                    activeStatuses
            );
            int concurrent = 0;
            for (Booking ob : branchOverlaps) {
                if (!ob.getService().getId().equals(service.getId()) && !service.isGroupService()) {
                    continue;
                }
                LocalDateTime bStartEff = bookingTime;
                LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                LocalDateTime obEndEff = ob.getEndTime().plusMinutes(ob.getService().getBufferMinutes());
                if (bStartEff.isBefore(obEndEff) && bEndEff.isAfter(ob.getBookingTime())) {
                    concurrent++;
                }
            }
            int maxConcurrent = service.getMaxConcurrent() > 0 ? service.getMaxConcurrent()
                    : (service.getCapacity() > 0 ? service.getCapacity() : 1);
            if (concurrent >= maxConcurrent) {
                throw new RuntimeException("No capacity left for this service at the selected time.");
            }
        }

        // 5. Price Computation (Custom Overrides + Peak surge multipliers)
        double basePrice = service.getPrice();
        if (staff != null) {
            StaffService mapping = staffServiceRepository.findByStaffAndService(staff, service).orElse(null);
            if (mapping != null && mapping.getPriceOverride() != null) {
                basePrice = mapping.getPriceOverride();
            }
        }

        // Apply peak pricing multiplier
        double multiplier = 1.0;
        List<TimeOfDayPricing> peakRules = timeOfDayPricingRepository.findByServiceAndDayOfWeek(service, dayNum);
        for (TimeOfDayPricing rule : peakRules) {
            if (startLocal.isBefore(rule.getEndTime()) && endLocal.isAfter(rule.getStartTime())) {
                multiplier = Math.max(multiplier, rule.getPriceMultiplier());
            }
        }
        
        double finalPrice = basePrice * multiplier;

        String paymentStatus = "UNPAID";
        CustomerPackage customerPackage = null;

        if (customerPackageId != null) {
            customerPackage = customerPackageRepository.findById(customerPackageId)
                    .orElseThrow(() -> new RuntimeException("Selected package was not found."));
            if (!customerPackage.getCustomer().getId().equals(customer.getId())) {
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
            
            // Check if service is included in this package
            boolean isIncluded = customerPackage.getServicePackage().getServices().stream()
                    .anyMatch(s -> s.getId().equals(service.getId()));
            if (!isIncluded) {
                throw new RuntimeException("This service is not included in the selected package.");
            }

            // Decrement sessions
            customerPackage.setSessionsRemaining(customerPackage.getSessionsRemaining() - 1);
            if (customerPackage.getSessionsRemaining() <= 0) {
                customerPackage.setStatus("EXHAUSTED");
            }
            customerPackageRepository.save(customerPackage);
            paymentStatus = "PAID (Package)";
        }

        // 6. Persist Booking
        Booking booking = Booking.builder()
                .customer(customer)
                .branch(branch)
                .service(service)
                .staff(staff)
                .bookingTime(bookingTime)
                .endTime(endTime)
                .price(finalPrice)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(paymentStatus)
                .customerPackage(customerPackage)
                .clientNotes(clientNotes)
                .build();

        Booking saved = bookingRepository.save(booking);
        slotLockService.release(lockKey);
        log.info("Booking created id={} customerId={} status={} paymentStatus={}",
                saved.getId(), customer.getId(), saved.getStatus(), saved.getPaymentStatus());
        return saved;
    }

    @Transactional
    public Booking rescheduleBooking(Long bookingId, LocalDateTime newTime, UserRole role, Long userId) {
        log.info("Reschedule requested bookingId={} newTime={} role={} userId={}", bookingId, newTime, role, userId);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found."));

        // Authorization check
        boolean authorized = false;
        if (role == UserRole.SUPER_ADMIN) {
            authorized = true;
        } else if (role == UserRole.CUSTOMER && booking.getCustomer().getId().equals(userId)) {
            authorized = true;
        } else if (role == UserRole.BUSINESS_OWNER) {
            if (booking.getBranch().getBusiness().getOwner().getId().equals(userId)) {
                authorized = true;
            }
        } else if (role == UserRole.BUSINESS_STAFF) {
            if (booking.getStaff() != null && booking.getStaff().getUser().getId().equals(userId)) {
                authorized = true;
            }
        }

        if (!authorized) {
            throw new RuntimeException("Unauthorized to reschedule this booking.");
        }

        Branch branch = booking.getBranch();
        Staff staff = booking.getStaff();
        com.hourslot.model.Service service = booking.getService();

        String lockKey = slotLockService.buildLockKey(branch.getId(), staff != null ? staff.getId() : null, service.getId(), newTime);
        if (!slotLockService.tryAcquire(lockKey)) {
            throw new RuntimeException("This slot is temporarily held by another customer. Please try again.");
        }

        try {
            LocalDateTime endTime = newTime.plusMinutes(service.getDurationMinutes());

            // 1. Basic Working Hours Validation
            DayOfWeek day = newTime.getDayOfWeek();
            int dayNum = day.getValue();
            
            List<WorkingHour> whs;
            if (staff != null) {
                whs = workingHourRepository.findByStaffOrderByDayOfWeekAsc(staff);
                if (whs.isEmpty()) {
                    whs = workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch);
                }
            } else {
                whs = workingHourRepository.findByBranchAndStaffIsNullOrderByDayOfWeekAsc(branch);
            }

            WorkingHour dayConfig = whs.stream()
                    .filter(w -> w.getDayOfWeek() == dayNum)
                    .findFirst()
                    .orElse(null);

            if (dayConfig == null || dayConfig.isClosed()) {
                throw new RuntimeException("The selected date/time is outside working hours (Closed).");
            }

            LocalTime startLocal = newTime.toLocalTime();
            LocalTime endLocal = endTime.toLocalTime();

            if (dayConfig.getStartTime() == null || dayConfig.getEndTime() == null ||
                    startLocal.isBefore(dayConfig.getStartTime()) || endLocal.isAfter(dayConfig.getEndTime())) {
                throw new RuntimeException("The selected slot is outside working shifts.");
            }

            // 2. Break Periods Validation
            if (dayConfig.getBreaks() != null) {
                for (Break br : dayConfig.getBreaks()) {
                    if (startLocal.isBefore(br.getEndTime()) && endLocal.isAfter(br.getStartTime())) {
                        throw new RuntimeException("The selected slot overlaps with a scheduled break.");
                    }
                }
            }

            // 3. Holiday Closure Validation
            List<Holiday> hols;
            if (staff != null) {
                hols = holidayRepository.findByStaffAndDate(staff, newTime.toLocalDate());
                if (hols.isEmpty()) {
                    hols = holidayRepository.findByBranchAndDate(branch, newTime.toLocalDate());
                }
            } else {
                hols = holidayRepository.findByBranchAndDate(branch, newTime.toLocalDate());
            }

            if (!hols.isEmpty()) {
                throw new RuntimeException("The selected date is scheduled as a holiday or absence.");
            }

            // 4. Concurrency & Overlapping Bookings Validation
            List<BookingStatus> activeStatuses = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

            if (staff != null) {
                List<Booking> overlapping = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                        staff,
                        newTime.minusMinutes(service.getBufferMinutes()).minusMinutes(service.getDurationMinutes()),
                        endTime.plusMinutes(service.getBufferMinutes()),
                        activeStatuses
                );

                for (Booking ob : overlapping) {
                    if (ob.getId().equals(bookingId)) {
                        continue; // Exclude current booking
                    }
                    LocalDateTime obStart = ob.getBookingTime();
                    LocalDateTime obEnd = ob.getEndTime();

                    LocalDateTime bStartEff = newTime;
                    LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                    LocalDateTime obStartEff = obStart;
                    LocalDateTime obEndEff = obEnd.plusMinutes(ob.getService().getBufferMinutes());

                    if (bStartEff.isBefore(obEndEff) && bEndEff.isAfter(obStartEff)) {
                        throw new RuntimeException("The selected staff member has an overlapping booking during this slot.");
                    }
                }
            } else {
                List<Booking> branchOverlaps = bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(
                        branch,
                        newTime.minusMinutes(service.getDurationMinutes()),
                        endTime.plusMinutes(service.getBufferMinutes()),
                        activeStatuses
                );
                int concurrent = 0;
                for (Booking ob : branchOverlaps) {
                    if (ob.getId().equals(bookingId)) {
                        continue; // Exclude current booking
                    }
                    if (!ob.getService().getId().equals(service.getId()) && !service.isGroupService()) {
                        continue;
                    }
                    LocalDateTime bStartEff = newTime;
                    LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes());
                    LocalDateTime obEndEff = ob.getEndTime().plusMinutes(ob.getService().getBufferMinutes());
                    if (bStartEff.isBefore(obEndEff) && bEndEff.isAfter(ob.getBookingTime())) {
                        concurrent++;
                    }
                }
                int maxConcurrent = service.getMaxConcurrent() > 0 ? service.getMaxConcurrent()
                        : (service.getCapacity() > 0 ? service.getCapacity() : 1);
                if (concurrent >= maxConcurrent) {
                    throw new RuntimeException("No capacity left for this service at the selected time.");
                }
            }

            // Update time
            booking.setBookingTime(newTime);
            booking.setEndTime(endTime);
            booking.setStatus(BookingStatus.CONFIRMED);

            Booking saved = bookingRepository.save(booking);
            slotLockService.release(lockKey);
            return saved;
        } catch (RuntimeException ex) {
            slotLockService.release(lockKey);
            throw ex;
        }
    }
}
