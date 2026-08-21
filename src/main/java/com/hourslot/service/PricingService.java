package com.hourslot.service;

import com.hourslot.model.Service;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffService;
import com.hourslot.model.TimeOfDayPricing;
import com.hourslot.repository.StaffServiceRepository;
import com.hourslot.repository.TimeOfDayPricingRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@Component
public class PricingService {

    public enum PricingKind {
        STANDARD,
        PEAK,
        OFF_PEAK
    }

    @Data
    @AllArgsConstructor
    public static class PriceQuote {
        private BigDecimal unitPrice;
        private BigDecimal priceMultiplier;
        private BigDecimal totalPrice;
        private PricingKind kind;
        private String pricingLabel;
        private String currency;
    }

    @Data
    public static class PricedSlot {
        private String startTime;
        private String endTime;
        private double basePrice;
        private double price;
        private double priceMultiplier;
        private String pricingKind;
        private String pricingLabel;
        private String currency;
    }

    private final TimeOfDayPricingRepository timeOfDayPricingRepository;
    private final StaffServiceRepository staffServiceRepository;
    private final EntitlementService entitlementService;

    public PricingService(
            TimeOfDayPricingRepository timeOfDayPricingRepository,
            StaffServiceRepository staffServiceRepository,
            EntitlementService entitlementService) {
        this.timeOfDayPricingRepository = timeOfDayPricingRepository;
        this.staffServiceRepository = staffServiceRepository;
        this.entitlementService = entitlementService;
    }

    public PriceQuote quote(Service service, Staff staff, LocalDateTime start, LocalDateTime end) {
        return quote(service, unitPrice(service, staff), rulesFor(service, start.getDayOfWeek().getValue()), start, end);
    }

    public PriceQuote quote(
            Service service,
            BigDecimal unitPrice,
            List<TimeOfDayPricing> dayRules,
            LocalDateTime start,
            LocalDateTime end) {
        AppliedRule applied = resolveRule(dayRules, start.toLocalTime(), end.toLocalTime());
        BigDecimal multiplier = applied == null ? BigDecimal.ONE : applied.multiplier();
        String currency = service.getCurrency() == null ? "USD" : service.getCurrency();
        BigDecimal total = unitPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        PricingKind kind = kindFor(multiplier);
        String label = applied == null ? null : displayLabel(applied.rule(), multiplier, kind);
        return new PriceQuote(unitPrice, multiplier, total, kind, label, currency);
    }

    public BigDecimal unitPrice(Service service, Staff staff) {
        BigDecimal unitPrice = service.getBasePrice() == null ? BigDecimal.ZERO : service.getBasePrice();
        if (staff != null) {
            StaffService mapping = staffServiceRepository.findByStaffAndService(staff, service).orElse(null);
            if (mapping != null && mapping.getPriceOverride() != null) {
                unitPrice = BigDecimal.valueOf(mapping.getPriceOverride());
            }
        }
        return unitPrice;
    }

    public List<TimeOfDayPricing> rulesFor(Service service, int dayOfWeek) {
        if (!allowsPeakPricing(service)) {
            return List.of();
        }
        return timeOfDayPricingRepository.findByServiceAndDayOfWeek(service, dayOfWeek);
    }

    private boolean allowsPeakPricing(Service service) {
        if (service == null || service.getBusiness() == null) {
            return false;
        }
        return entitlementService.allows(service.getBusiness().getOrganization(), EntitlementService.PEAK_PRICING);
    }

    public PricedSlot toSlot(LocalTime start, LocalTime end, PriceQuote quote) {
        PricedSlot slot = new PricedSlot();
        slot.setStartTime(String.format("%02d:%02d", start.getHour(), start.getMinute()));
        slot.setEndTime(String.format("%02d:%02d", end.getHour(), end.getMinute()));
        slot.setBasePrice(quote.getUnitPrice().doubleValue());
        slot.setPrice(quote.getTotalPrice().doubleValue());
        slot.setPriceMultiplier(quote.getPriceMultiplier().doubleValue());
        slot.setPricingKind(quote.getKind().name());
        slot.setPricingLabel(quote.getPricingLabel());
        slot.setCurrency(quote.getCurrency());
        return slot;
    }

    private AppliedRule resolveRule(List<TimeOfDayPricing> rules, LocalTime start, LocalTime end) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        AppliedRule chosen = null;
        for (TimeOfDayPricing rule : rules) {
            if (!rule.isActive() || rule.getStartTime() == null || rule.getEndTime() == null) {
                continue;
            }
            if (!(start.isBefore(rule.getEndTime()) && end.isAfter(rule.getStartTime()))) {
                continue;
            }
            BigDecimal candidate = BigDecimal.valueOf(rule.getPriceMultiplier());
            if (chosen == null) {
                chosen = new AppliedRule(rule, candidate);
                continue;
            }
            BigDecimal current = chosen.multiplier();
            boolean candidateIsPeak = candidate.compareTo(BigDecimal.ONE) > 0;
            boolean currentIsPeak = current.compareTo(BigDecimal.ONE) > 0;
            if (candidateIsPeak && (!currentIsPeak || candidate.compareTo(current) > 0)) {
                chosen = new AppliedRule(rule, candidate);
            } else if (!candidateIsPeak && !currentIsPeak && candidate.compareTo(current) < 0) {
                chosen = new AppliedRule(rule, candidate);
            }
        }
        return chosen;
    }

    private static PricingKind kindFor(BigDecimal multiplier) {
        if (multiplier.compareTo(BigDecimal.ONE) > 0) {
            return PricingKind.PEAK;
        }
        if (multiplier.compareTo(BigDecimal.ONE) < 0) {
            return PricingKind.OFF_PEAK;
        }
        return PricingKind.STANDARD;
    }

    private static String displayLabel(TimeOfDayPricing rule, BigDecimal multiplier, PricingKind kind) {
        if (rule.getLabel() != null && !rule.getLabel().isBlank()) {
            return rule.getLabel().trim();
        }
        int percent = multiplier.subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        if (kind == PricingKind.PEAK) {
            return String.format(Locale.US, "Peak +%d%%", percent);
        }
        if (kind == PricingKind.OFF_PEAK) {
            return String.format(Locale.US, "Off-peak %d%%", percent);
        }
        return null;
    }

    private record AppliedRule(TimeOfDayPricing rule, BigDecimal multiplier) {
    }
}
