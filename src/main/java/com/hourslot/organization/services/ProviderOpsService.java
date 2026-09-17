package com.hourslot.organization.services;

import com.hourslot.booking.model.Booking;
import com.hourslot.booking.repository.BookingRepository;
import com.hourslot.job.model.Job;
import com.hourslot.job.services.JobService;
import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import com.hourslot.payment.repository.PaymentRepository;
import com.hourslot.quote.model.Quote;
import com.hourslot.quote.repository.QuoteRepository;
import com.hourslot.request.repository.ServiceRequestRepository;
import com.hourslot.request.services.RequestMatchingService;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProviderOpsService {
    private static final Set<String> OPS_STATUSES = Set.of("AVAILABLE", "BUSY", "UNAVAILABLE", "VACATION");

    private final BookingRepository bookingRepository;
    private final JobService jobService;
    private final RequestMatchingService matchingService;
    private final QuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final ServiceRequestRepository requestRepository;
    private final BusinessRepository businessRepository;
    private final JdbcSupport jdbc;

    public ProviderOpsService(
            BookingRepository bookingRepository,
            JobService jobService,
            RequestMatchingService matchingService,
            QuoteRepository quoteRepository,
            PaymentRepository paymentRepository,
            ServiceRequestRepository requestRepository,
            BusinessRepository businessRepository,
            JdbcSupport jdbc) {
        this.bookingRepository = bookingRepository;
        this.jobService = jobService;
        this.matchingService = matchingService;
        this.quoteRepository = quoteRepository;
        this.paymentRepository = paymentRepository;
        this.requestRepository = requestRepository;
        this.businessRepository = businessRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> today(Business business) {
        LocalDate today = LocalDate.now();
        return calendar(business, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calendar(Business business, LocalDateTime start, LocalDateTime end) {
        List<Booking> bookings = bookingRepository.findByBusinessAndBookingTimeBetween(
                business.getId(), start, end);
        List<Job> jobs = jobService.todayForProvider(business.getId(), start, end);
        List<Map<String, Object>> openRequests = matchingService.inboxForProvider(business).stream()
                .filter(row -> {
                    Object link = row.get("link");
                    if (link instanceof com.hourslot.request.model.ServiceRequestProvider srp) {
                        String status = srp.getResponseStatus();
                        return status == null || "INVITED".equalsIgnoreCase(status) || "VIEWED".equalsIgnoreCase(status);
                    }
                    return true;
                })
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", start.toString());
        result.put("to", end.toString());
        result.put("date", start.toLocalDate().toString());
        result.put("opsStatus", business.getOpsStatus());
        result.put("bookings", bookings);
        result.put("jobs", jobs);
        result.put("openRequests", openRequests);
        result.put("counts", Map.of(
                "bookings", bookings.size(),
                "jobs", jobs.size(),
                "openRequests", openRequests.size()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analytics(Business business) {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("opsStatus", business.getOpsStatus());
        result.put("bookingsThisMonth", jdbc.count("""
                SELECT COUNT(*) FROM bookings
                WHERE business_id = :id AND deleted_at IS NULL AND booking_time >= :start
                """, jdbc.params().addValue("id", business.getId()).addValue("start", JdbcSupport.ts(monthStart))));
        result.put("completedBookings", jdbc.count("""
                SELECT COUNT(*) FROM bookings
                WHERE business_id = :id AND deleted_at IS NULL AND UPPER(status) = 'COMPLETED'
                """, jdbc.params().addValue("id", business.getId())));
        result.put("jobsCompleted", jdbc.count("""
                SELECT COUNT(*) FROM jobs WHERE provider_id = :id AND UPPER(status) = 'COMPLETED'
                """, jdbc.params().addValue("id", business.getId())));
        result.put("quotesSent", jdbc.count("""
                SELECT COUNT(*) FROM quotes WHERE provider_id = :id AND deleted_at IS NULL
                """, jdbc.params().addValue("id", business.getId())));
        result.put("openRequests", matchingService.inboxForProvider(business).size());
        result.put("rating", business.getRating());
        result.put("reviewCount", business.getRatingCount());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> quotes(Business business) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Quote quote : quoteRepository.findByProvider(business.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quote", quote);
            requestRepository.findById(quote.getRequestId()).ifPresent(request -> row.put("request", request));
            rows.add(row);
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<?> payments(Business business) {
        return paymentRepository.findByBusinessId(business.getId());
    }

    @Transactional
    public Business updateOpsStatus(Business business, String opsStatus) {
        if (opsStatus == null || opsStatus.isBlank()) {
            throw new IllegalArgumentException("opsStatus is required.");
        }
        String normalized = opsStatus.trim().toUpperCase(Locale.ROOT);
        if (!OPS_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("opsStatus must be AVAILABLE, BUSY, UNAVAILABLE, or VACATION.");
        }
        business.setOpsStatus(normalized);
        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> customers(Business business) {
        return jdbc.jdbc().queryForList("""
                SELECT DISTINCT u.id AS customer_id, u.email, u.first_name, u.last_name, u.phone_number,
                       MAX(b.booking_time) AS last_booking_at,
                       COUNT(b.id) AS booking_count
                FROM bookings b
                JOIN users u ON u.id = b.customer_user_id
                WHERE b.business_id = :businessId AND b.deleted_at IS NULL
                GROUP BY u.id, u.email, u.first_name, u.last_name, u.phone_number
                ORDER BY last_booking_at DESC NULLS LAST
                """, Map.of("businessId", business.getId()));
    }
}
