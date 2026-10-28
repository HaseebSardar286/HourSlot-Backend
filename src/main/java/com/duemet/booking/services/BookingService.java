package com.duemet.booking.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingItem;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.model.BookingStatusHistory;
import com.duemet.booking.repository.BookingStatusHistoryRepository;
import com.duemet.organization.model.Branch;
import com.duemet.organization.repository.BranchRepository;
import com.duemet.organization.model.Business;
import com.duemet.organization.repository.BusinessRepository;
import com.duemet.organization.model.BusinessStatus;
import com.duemet.catalog.model.CustomerPackage;
import com.duemet.catalog.repository.CustomerPackageRepository;
import com.duemet.identity.model.CustomerProfile;
import com.duemet.identity.repository.CustomerProfileRepository;
import com.duemet.catalog.services.PricingService;
import com.duemet.availability.services.ScheduleService;
import com.duemet.catalog.repository.ServiceRepository;
import com.duemet.organization.model.Staff;
import com.duemet.organization.repository.StaffRepository;
import com.duemet.catalog.model.StaffService;
import com.duemet.catalog.repository.StaffServiceRepository;
import com.duemet.organization.services.TenancyService;
import com.duemet.identity.model.User;
import com.duemet.identity.repository.UserRepository;
import com.duemet.identity.model.UserRole;
import com.duemet.shared.exception.BookingConflictException;
import com.duemet.booking.model.BookingVisit;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.catalog.services.ServiceModes;
import com.duemet.identity.model.CustomerAddress;
import com.duemet.identity.repository.CustomerAddressRepository;
import com.duemet.job.services.JobService;
import org.springframework.context.annotation.Lazy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    private static final Logger log = LogManager.getLogger(BookingService.class);

    /** Bookings starting up to this long before a slot are fetched, so long jobs that began earlier still collide. */
    private static final long OVERLAP_LOOKBACK_MINUTES = 24 * 60;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private StaffServiceRepository staffServiceRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private CustomerPackageRepository customerPackageRepository;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private TenancyService tenancyService;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    private BookingVisitRepository bookingVisitRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    @Autowired
    @Lazy
    private JobService jobService;

    @Autowired
    private BookingPolicyService bookingPolicyService;

    @Autowired
    private com.duemet.marketing.PromoCodeService promoCodeService;

    /**
     * Home-visit / urgent options for a booking.
     * @param addressId customer address the provider should travel to
     * @param urgent book against the provider's "available now" window, with the service's urgent surcharge
     * @param bookedByCustomer the customer is booking for themselves (address then mandatory for home-only services)
     * @param promoCode optional business promo code; not combined with package sessions
     */
    public record VisitRequest(Long addressId, boolean urgent, boolean bookedByCustomer, String promoCode) {
        public VisitRequest(Long addressId, boolean urgent, boolean bookedByCustomer) {
            this(addressId, urgent, bookedByCustomer, null);
        }
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId, LocalDateTime bookingTime) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime, null, null, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime, clientNotes, null, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes, Long customerPackageId) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime,
                clientNotes, customerPackageId, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes, Long customerPackageId,
                                 String preferredPaymentMethod) {
        return createBooking(customerId, branchId, serviceId, staffId, bookingTime,
                clientNotes, customerPackageId, preferredPaymentMethod, null);
    }

    @Transactional
    public Booking createBooking(Long customerId, Long branchId, Long serviceId, Long staffId,
                                 LocalDateTime bookingTime, String clientNotes, Long customerPackageId,
                                 String preferredPaymentMethod, VisitRequest visit) {
        return createBookingInternalEntry(customerId, branchId, serviceId, staffId, bookingTime,
                clientNotes, customerPackageId, preferredPaymentMethod, "DIRECT", null, null, null, false, visit);
    }

    @Transactional
    public Booking createBookingFromRequest(
            Long customerId,
            Long branchId,
            Long serviceId,
            Long staffId,
            LocalDateTime bookingTime,
            String clientNotes,
            Long serviceRequestId,
            Long quoteId,
            BigDecimal quotedAmount) {
        return createBookingInternalEntry(customerId, branchId, serviceId, staffId, bookingTime,
                clientNotes, null, "VENUE", "REQUEST", serviceRequestId, quoteId, quotedAmount, true, null);
    }

    private Booking createBookingInternalEntry(
            Long customerId, Long branchId, Long serviceId, Long staffId,
            LocalDateTime bookingTime, String clientNotes, Long customerPackageId,
            String preferredPaymentMethod,
            String source, Long serviceRequestId, Long quoteId, BigDecimal quotedAmount,
            boolean allowQuoteService, VisitRequest visit) {
        log.info("Creating booking customerId={} branchId={} serviceId={} staffId={} time={} packageId={} source={} pay={}",
                customerId, branchId, serviceId, staffId, bookingTime, customerPackageId, source, preferredPaymentMethod);
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found."));
        customerProfileRepository.findById(customerId)
                .orElseGet(() -> customerProfileRepository.save(CustomerProfile.builder().user(customer).build()));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found."));
        if (branch.getBusiness() == null || branch.getBusiness().getId() == null) {
            throw new RuntimeException("Branch is not linked to a business.");
        }
        Business business = businessRepository.findById(branch.getBusiness().getId())
                .orElseThrow(() -> new RuntimeException("Business not found."));
        branch.setBusiness(business);
        com.duemet.catalog.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("com.duemet.catalog.model.Service not found."));

        if (!allowQuoteService && (service.isRequiresQuote() || "QUOTE".equalsIgnoreCase(service.getPricingType()))) {
            throw new RuntimeException("This service requires a quote and cannot be booked on a fixed slot yet.");
        }

        if (business.getStatus() != BusinessStatus.APPROVED) {
            throw new RuntimeException("This business is not currently accepting bookings.");
        }
        if (!"PUBLISHED".equalsIgnoreCase(business.getPublishStatus())) {
            throw new RuntimeException("This listing is not published and cannot accept new bookings.");
        }
        if (business.getIndividualDisabledReason() != null && !business.getIndividualDisabledReason().isBlank()) {
            throw new RuntimeException("This listing is no longer available for booking.");
        }

        Staff staff = null;
        if (staffId != null) {
            staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> new RuntimeException("Staff not found."));
            if (!staff.getBranch().getId().equals(branchId)) {
                throw new RuntimeException("Staff does not belong to the selected branch.");
            }
        }

        Booking booking = createBookingInternal(customer, branch, service, staff, bookingTime, clientNotes,
                customerPackageId, preferredPaymentMethod, visit);
        booking.setSource(source == null ? "DIRECT" : source);
        booking.setServiceRequestId(serviceRequestId);
        booking.setQuoteId(quoteId);
        if (quotedAmount != null) {
            booking.setTotalPrice(quotedAmount);
            if (booking.getItems() != null && !booking.getItems().isEmpty()) {
                booking.getItems().get(0).setUnitPrice(quotedAmount);
                booking.getItems().get(0).setLineTotal(quotedAmount);
            }
        }
        Booking saved = bookingRepository.save(booking);
        BookingVisit savedVisit = bookingVisitRepository.findByBookingId(saved.getId()).orElse(null);
        if (savedVisit != null && savedVisit.getAddressText() != null) {
            // Home visits are tracked as jobs (on the way, arrived, done, paid), like quote-request jobs.
            jobService.createForVisit(saved);
        }
        return saved;
    }

    private Booking createBookingInternal(User customer, Branch branch, com.duemet.catalog.model.Service service,
                                          Staff staff, LocalDateTime bookingTime, String clientNotes,
                                          Long customerPackageId, String preferredPaymentMethod,
                                          VisitRequest visit) {
        LocalDateTime endTime = bookingTime.plusMinutes(service.getDurationMinutes());
        requireFuture(bookingTime);
        String paymentMethod = normalizePaymentMethod(preferredPaymentMethod);
        bookingPolicyService.requirePrepaymentIfNeeded(customer.getId(), branch.getBusiness().getId(),
                customerPackageId != null ? "PACKAGE" : paymentMethod);
        CustomerAddress visitAddress = resolveVisitAddress(customer, service, visit);
        boolean urgent = visit != null && visit.urgent();
        if (urgent) {
            requireAvailableNow(branch.getBusiness(), bookingTime);
        }
        if (staff == null) {
            staff = assignAvailableStaff(branch, service, bookingTime, endTime, null);
        }

        // Held until commit, so a concurrent booking cannot pass validation before this one is visible.
        lockSlotScope(branch, staff, service);
        validateSlot(branch, staff, service, bookingTime, endTime, null);

        PricingService.PriceQuote quote = pricingService.quote(service, staff, bookingTime, endTime);
        BigDecimal basePrice = quote.getUnitPrice();
        BigDecimal multiplier = quote.getPriceMultiplier();
        BigDecimal urgentFee = urgent ? urgentFee(service, quote.getTotalPrice()) : BigDecimal.ZERO;
        BigDecimal finalPrice = quote.getTotalPrice().add(urgentFee);

        String paymentStatus = "UNPAID";
        CustomerPackage customerPackage = null;
        BookingStatus initialStatus = "ONLINE".equals(paymentMethod)
                ? BookingStatus.PENDING
                : BookingStatus.CONFIRMED;

        if (customerPackageId != null) {
            customerPackage = customerPackageRepository.findById(customerPackageId)
                    .orElseThrow(() -> new RuntimeException("Selected package was not found."));
            if (!customerPackage.getCustomerUser().getId().equals(customer.getId())) {
                throw new RuntimeException("Selected package does not belong to this customer.");
            }
            if (!"ACTIVE".equals(customerPackage.getStatus()) || customerPackage.getSessionsRemaining() <= 0) {
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

            if (!customerPackageRepository.consumeSession(customerPackage.getId(),
                    customerPackage.getVersion() == null ? 1 : customerPackage.getVersion())) {
                throw new RuntimeException("This package was just used by another booking. Please try again.");
            }
            customerPackage = customerPackageRepository.findById(customerPackageId).orElse(customerPackage);
            paymentStatus = "PAID (Package)";
            paymentMethod = "PACKAGE";
            initialStatus = BookingStatus.CONFIRMED;
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
                .currency(service.getCurrency() == null
                        ? (business.getOrganization() != null && business.getOrganization().getDefaultCurrency() != null
                                ? business.getOrganization().getDefaultCurrency()
                                : "USD")
                        : service.getCurrency())
                .status(initialStatus)
                .paymentStatus(paymentStatus)
                .paymentMethod(paymentMethod)
                .source("DIRECT")
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

        if (visit != null && visit.promoCode() != null && !visit.promoCode().isBlank() && customerPackage == null) {
            var applied = promoCodeService.redeem(business.getId(), customer.getId(), visit.promoCode(),
                    saved.getTotalPrice(), saved.getId());
            saved.setTotalPrice(applied.finalPrice());
            saved = bookingRepository.save(saved);
        }

        if (visitAddress != null || urgent) {
            bookingVisitRepository.save(BookingVisit.builder()
                    .bookingId(saved.getId())
                    .customerAddressId(visitAddress == null ? null : visitAddress.getId())
                    .addressText(visitAddress == null ? null : formatAddress(visitAddress))
                    .latitude(visitAddress == null ? null : visitAddress.getLatitude())
                    .longitude(visitAddress == null ? null : visitAddress.getLongitude())
                    .arrivalWindowMinutes(visitAddress == null ? 0 : Math.max(0, service.getArrivalWindowMinutes()))
                    .urgent(urgent)
                    .urgentFee(urgentFee)
                    .build());
        }

        bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(saved)
                .fromStatus(null)
                .toStatus(saved.getStatus().name())
                .changedBy(customer)
                .reason("ONLINE".equals(paymentMethod) ? "CREATED_AWAITING_PAYMENT" : "CREATED")
                .build());

        log.info("Booking created id={} customerId={} status={} paymentStatus={}",
                saved.getId(), customer.getId(), saved.getStatus(), saved.getPaymentStatus());
        return saved;
    }

    private static final java.util.Set<String> CLIENT_PAYMENT_METHODS = java.util.Set.of("VENUE", "ONLINE");

    /** Only methods a customer may pick; PACKAGE is set server-side when a package session is consumed. */
    private static String normalizePaymentMethod(String preferred) {
        if (preferred == null || preferred.isBlank()) {
            return "VENUE";
        }
        String method = preferred.trim().toUpperCase(java.util.Locale.ROOT);
        if (!CLIENT_PAYMENT_METHODS.contains(method)) {
            throw new IllegalArgumentException("Unsupported payment method: " + preferred);
        }
        return method;
    }

    private CustomerAddress resolveVisitAddress(User customer, com.duemet.catalog.model.Service service,
                                                VisitRequest visit) {
        Long addressId = visit == null ? null : visit.addressId();
        if (addressId == null) {
            if (ServiceModes.requiresHomeVisit(service) && visit != null && visit.bookedByCustomer()) {
                throw new IllegalArgumentException("This service is done at your location. Please choose an address.");
            }
            return null;
        }
        if (!ServiceModes.supportsHomeVisit(service)) {
            throw new IllegalArgumentException("This service is only available at the provider's location.");
        }
        return customerAddressRepository.findByIdAndUser(addressId, customer)
                .orElseThrow(() -> new IllegalArgumentException("Selected address was not found."));
    }

    private void requireAvailableNow(Business business, LocalDateTime bookingTime) {
        Business fresh = businessRepository.findById(business.getId()).orElse(business);
        LocalDateTime until = fresh.getAvailableNowUntil();
        if (until == null || !until.isAfter(LocalDateTime.now())) {
            throw new BookingConflictException("This provider is not taking urgent jobs right now.");
        }
        if (bookingTime.isAfter(until)) {
            throw new BookingConflictException("Urgent bookings must start before the provider's availability ends.");
        }
    }

    public static BigDecimal urgentFee(com.duemet.catalog.model.Service service, BigDecimal price) {
        BigDecimal pct = service.getUrgentSurchargePercent();
        if (pct == null || pct.signum() <= 0 || price == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(pct).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    public static String formatAddress(CustomerAddress address) {
        return java.util.stream.Stream.of(address.getAddressLine(), address.getAreaName(), address.getCity())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    /** Same travel gap the public slot list applies, so a hidden slot cannot be booked directly. */
    private int travelBufferMinutes(Branch branch, com.duemet.catalog.model.Service service) {
        if (!ServiceModes.supportsHomeVisit(service) || branch.getBusiness() == null
                || branch.getBusiness().getId() == null) {
            return 0;
        }
        return businessRepository.findById(branch.getBusiness().getId())
                .map(Business::getTravelBufferMinutes)
                .orElse(0);
    }

    private static void requireFuture(LocalDateTime start) {
        if (!start.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("The selected time is in the past. Please choose a future slot.");
        }
    }

    private void lockSlotScope(Branch branch, Staff staff, com.duemet.catalog.model.Service service) {
        bookingRepository.lockSlotScope(staff != null
                ? "booking:staff:" + staff.getId()
                : "booking:branch:" + branch.getId() + ":service:" + service.getId());
    }

    private Staff assignAvailableStaff(Branch branch, com.duemet.catalog.model.Service service,
                                       LocalDateTime bookingTime, LocalDateTime endTime, Long excludeBookingId) {
        if (service.isGroupService()) {
            return null;
        }
        List<Staff> eligible = new ArrayList<>();
        for (StaffService mapping : staffServiceRepository.findByService(service)) {
            Staff member = mapping.getStaff();
            if (member != null && member.getBranch() != null && branch.getId().equals(member.getBranch().getId())) {
                eligible.add(member);
            }
        }
        if (eligible.isEmpty()) {
            eligible.addAll(staffRepository.findByBranch(branch));
        }
        if (eligible.isEmpty()) {
            return null;
        }
        Staff best = null;
        BigDecimal bestPrice = null;
        for (Staff candidate : eligible) {
            if (!candidate.isActive()) continue;
            try {
                validateSlot(branch, candidate, service, bookingTime, endTime, excludeBookingId);
            } catch (RuntimeException ex) {
                continue;
            }
            BigDecimal price = pricingService.unitPrice(service, candidate);
            if (best == null || price.compareTo(bestPrice) < 0) {
                best = candidate;
                bestPrice = price;
            }
        }
        if (best == null) {
            throw new BookingConflictException("That time is already booked. Please choose another slot.");
        }
        return best;
    }

    private void validateSlot(Branch branch, Staff staff, com.duemet.catalog.model.Service service,
                              LocalDateTime bookingTime, LocalDateTime endTime, Long excludeBookingId) {
        int dayNum = bookingTime.getDayOfWeek().getValue();
        ScheduleService.DayHours dayConfig = scheduleService.resolveDayHours(branch, staff, dayNum)
                .orElse(null);
        if (dayConfig == null || dayConfig.isClosed()) {
            throw new RuntimeException("The selected date/time is outside working hours (Closed).");
        }

        LocalTime startLocal = bookingTime.toLocalTime();
        LocalTime endLocal = endTime.toLocalTime();

        List<ScheduleService.TimeWindow> intervals = dayConfig.getIntervals();
        if (intervals == null || intervals.isEmpty()) {
            if (dayConfig.getStartTime() == null || dayConfig.getEndTime() == null ||
                    startLocal.isBefore(dayConfig.getStartTime()) || endLocal.isAfter(dayConfig.getEndTime())) {
                throw new RuntimeException("The selected slot is outside working shifts.");
            }
        } else {
            boolean insideInterval = false;
            for (ScheduleService.TimeWindow window : intervals) {
                if (window.getStartTime() == null || window.getEndTime() == null) continue;
                if (!startLocal.isBefore(window.getStartTime()) && !endLocal.isAfter(window.getEndTime())) {
                    insideInterval = true;
                    break;
                }
            }
            if (!insideInterval) {
                throw new RuntimeException("The selected slot is outside working shifts.");
            }
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

        int travel = travelBufferMinutes(branch, service);
        if (staff != null) {
            List<Booking> overlapping = bookingRepository.findByStaffAndBookingTimeBetweenAndStatusIn(
                    staff,
                    bookingTime.minusMinutes(OVERLAP_LOOKBACK_MINUTES),
                    endTime.plusMinutes(service.getBufferMinutes() + travel),
                    activeStatuses
            );
            for (Booking ob : overlapping) {
                if (excludeBookingId != null && ob.getId().equals(excludeBookingId)) {
                    continue;
                }
                com.duemet.catalog.model.Service obService = ob.getService();
                LocalDateTime bEndEff = endTime.plusMinutes(service.getBufferMinutes() + travel);
                LocalDateTime obEndEff = ob.getEndTime().plusMinutes(
                        (obService == null ? 0 : obService.getBufferMinutes()) + travel);
                if (bookingTime.isBefore(obEndEff) && bEndEff.isAfter(ob.getBookingTime())) {
                    throw new BookingConflictException("The selected staff member has an overlapping booking during this slot.");
                }
            }
        } else {
            List<Booking> branchOverlaps = bookingRepository.findByBranchAndBookingTimeBetweenAndStatusIn(
                    branch,
                    bookingTime.minusMinutes(OVERLAP_LOOKBACK_MINUTES),
                    endTime.plusMinutes(service.getBufferMinutes()),
                    activeStatuses
            );
            int concurrent = 0;
            for (Booking ob : branchOverlaps) {
                if (excludeBookingId != null && ob.getId().equals(excludeBookingId)) {
                    continue;
                }
                com.duemet.catalog.model.Service obService = ob.getService();
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
                throw new BookingConflictException("No capacity left for this service at the selected time.");
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
            User owner = tenancyService.findOwner(booking.resolvedBusiness()).orElse(null);
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
        com.duemet.catalog.model.Service service = booking.getService();
        if (service == null) {
            throw new RuntimeException("Booking is missing service data.");
        }

        BookingStatus previous = booking.getStatus();
        if (previous != BookingStatus.PENDING && previous != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only pending or confirmed bookings can be rescheduled.");
        }
        requireFuture(newTime);

        LocalDateTime endTime = newTime.plusMinutes(service.getDurationMinutes());
        if (staff == null) {
            staff = assignAvailableStaff(branch, service, newTime, endTime, bookingId);
            if (staff != null && booking.primaryItem() != null) {
                booking.primaryItem().setStaff(staff);
            }
        }
        lockSlotScope(branch, staff, service);
        validateSlot(branch, staff, service, newTime, endTime, bookingId);

        booking.setBookingTime(newTime);
        booking.setEndTime(endTime);
        // Status is kept: an unpaid online booking must stay PENDING until the payment webhook confirms it.
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
        return saved;
    }
}
