package com.duemet.booking.services;

import com.duemet.booking.repository.BookingRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Releases slots held by abandoned Stripe checkouts (PENDING + ONLINE + UNPAID).
 */
@Component
public class UnpaidBookingExpiryJob {

    private static final Logger log = LogManager.getLogger(UnpaidBookingExpiryJob.class);
    private static final int EXPIRY_MINUTES = 20;

    private final BookingRepository bookingRepository;
    private final com.duemet.marketing.PromoCodeService promoCodeService;

    public UnpaidBookingExpiryJob(BookingRepository bookingRepository,
                                  com.duemet.marketing.PromoCodeService promoCodeService) {
        this.bookingRepository = bookingRepository;
        this.promoCodeService = promoCodeService;
    }

    @Scheduled(fixedDelayString = "${app.booking.unpaid-expiry-ms:60000}")
    public void expireAbandonedOnlineBookings() {
        int expired = bookingRepository.expireUnpaidOnlinePending(
                LocalDateTime.now().minusMinutes(EXPIRY_MINUTES));
        if (expired > 0) {
            log.info("Expired {} unpaid online booking(s) older than {} minutes", expired, EXPIRY_MINUTES);
            promoCodeService.releaseForCancelledBookings();
        }
    }
}
