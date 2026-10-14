package com.hourslot.organization.services;

import com.hourslot.organization.model.Business;
import com.hourslot.organization.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ProviderPresenceService {
    public static final long IDLE_TIMEOUT_MINUTES = 5;

    private final BusinessRepository businessRepository;

    public ProviderPresenceService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Transactional
    public Map<String, Object> updatePresence(
            Business business,
            boolean online,
            Double latitude,
            Double longitude,
            String source) {
        if (TenancyService.isIndividualDisabled(business)) {
            throw new IllegalStateException("Presence cannot be updated for a disabled individual listing.");
        }
        String normalizedSource = source == null || source.isBlank()
                ? (online ? "HEARTBEAT" : "MANUAL_OFFLINE")
                : source.trim().toUpperCase(Locale.ROOT);

        business.setOnline(online);
        business.setLastSeenAt(LocalDateTime.now());
        business.setPresenceSource(normalizedSource);
        if (latitude != null) {
            business.setLastLatitude(latitude);
        }
        if (longitude != null) {
            business.setLastLongitude(longitude);
        }
        if (!online && "MANUAL_OFFLINE".equals(normalizedSource)) {
            // keep last coords
        }
        businessRepository.save(business);
        return snapshot(business);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(Business business) {
        boolean effectivelyOnline = isEffectivelyOnline(business);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isOnline", effectivelyOnline);
        result.put("lastSeenAt", business.getLastSeenAt());
        result.put("lastLatitude", business.getLastLatitude());
        result.put("lastLongitude", business.getLastLongitude());
        result.put("presenceSource", business.getPresenceSource());
        result.put("opsStatus", business.getOpsStatus());
        return result;
    }

    public boolean isEffectivelyOnline(Business business) {
        if (business == null || !business.isOnline() || TenancyService.isIndividualDisabled(business)) {
            return false;
        }
        if (business.getLastSeenAt() == null) {
            return false;
        }
        return business.getLastSeenAt().isAfter(LocalDateTime.now().minusMinutes(IDLE_TIMEOUT_MINUTES));
    }

    @Transactional
    public void markOfflineIfStale(Business business) {
        if (business == null || !business.isOnline()) {
            return;
        }
        if (!isEffectivelyOnline(business)) {
            business.setOnline(false);
            business.setPresenceSource("TIMEOUT");
            businessRepository.save(business);
        }
    }
}
