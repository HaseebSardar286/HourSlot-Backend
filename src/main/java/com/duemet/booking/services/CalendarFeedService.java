package com.duemet.booking.services;

import com.duemet.booking.model.Booking;
import com.duemet.booking.model.BookingStatus;
import com.duemet.booking.repository.BookingRepository;
import com.duemet.booking.repository.BookingVisitRepository;
import com.duemet.booking.repository.CalendarFeedRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

/**
 * Private iCalendar (.ics) feed of a business's bookings. Google Calendar, Outlook and Apple Calendar
 * can subscribe to the URL, so appointments show up next to the provider's personal events.
 */
@Service
public class CalendarFeedService {

    private static final DateTimeFormatter ICS_UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Set<BookingStatus> SHOWN = Set.of(
            BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

    private final CalendarFeedRepository feedRepository;
    private final BookingRepository bookingRepository;
    private final BookingVisitRepository visitRepository;
    private final SecureRandom random = new SecureRandom();

    public CalendarFeedService(CalendarFeedRepository feedRepository, BookingRepository bookingRepository,
                               BookingVisitRepository visitRepository) {
        this.feedRepository = feedRepository;
        this.bookingRepository = bookingRepository;
        this.visitRepository = visitRepository;
    }

    public String tokenFor(Long businessId) {
        return feedRepository.tokenFor(businessId).orElseGet(() -> rotate(businessId));
    }

    /** New secret URL; the old one stops working (use if the link was shared by mistake). */
    public String rotate(Long businessId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        feedRepository.setToken(businessId, token);
        return token;
    }

    public Optional<String> render(String token) {
        return feedRepository.businessForToken(token).map(this::renderBusiness);
    }

    private String renderBusiness(Long businessId) {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//Duemet//Bookings//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:Duemet bookings");
        line(ics, "REFRESH-INTERVAL;VALUE=DURATION:PT30M");
        String stamp = utc(now);
        for (Booking b : bookingRepository.findByBusinessAndBookingTimeBetween(
                businessId, now.minusDays(30), now.plusDays(180))) {
            if (b.getStatus() == null || !SHOWN.contains(b.getStatus()) || b.getBookingTime() == null) {
                continue;
            }
            String service = b.getService() != null ? b.getService().getName() : "Booking";
            String customer = b.getCustomerUser() == null ? null
                    : ((nz(b.getCustomerUser().getFirstName()) + " " + nz(b.getCustomerUser().getLastName())).trim());
            String location = visitRepository.findByBookingId(b.getId())
                    .map(v -> v.getAddressText())
                    .filter(a -> a != null && !a.isBlank())
                    .orElse(b.getBranch() != null ? b.getBranch().getAddress() : null);
            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:booking-" + b.getId() + "@duemet");
            line(ics, "DTSTAMP:" + stamp);
            line(ics, "DTSTART:" + utc(b.getBookingTime()));
            line(ics, "DTEND:" + utc(b.getEndTime() != null ? b.getEndTime() : b.getBookingTime().plusHours(1)));
            line(ics, "SUMMARY:" + escape(service + (customer == null || customer.isEmpty() ? "" : " — " + customer)));
            if (location != null && !location.isBlank()) {
                line(ics, "LOCATION:" + escape(location));
            }
            line(ics, "DESCRIPTION:" + escape("Status: " + b.getStatus()
                    + (b.getPublicCode() != null ? "\nRef: " + b.getPublicCode() : "")
                    + (b.getClientNotes() != null && !b.getClientNotes().isBlank() ? "\nNotes: " + b.getClientNotes() : "")));
            line(ics, "STATUS:" + (b.getStatus() == BookingStatus.PENDING ? "TENTATIVE" : "CONFIRMED"));
            line(ics, "END:VEVENT");
        }
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static String utc(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).format(ICS_UTC);
    }

    static String escape(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    /** RFC 5545: CRLF line endings, lines folded at 75 octets. */
    private static void line(StringBuilder out, String content) {
        int limit = 75;
        while (content.length() > limit) {
            out.append(content, 0, limit).append("\r\n ");
            content = content.substring(limit);
            limit = 74;
        }
        out.append(content).append("\r\n");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
