package com.hourslot.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SlotLockService {

    private static final Logger log = LogManager.getLogger(SlotLockService.class);
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public String buildLockKey(Long branchId, Long staffId, Long serviceId, LocalDateTime bookingTime) {
        String staffPart = staffId != null ? staffId.toString() : "any";
        return "booking:slot:" + branchId + ":" + staffPart + ":" + serviceId + ":" + bookingTime.format(FMT);
    }

    public boolean tryAcquire(String lockKey) {
        if (redisTemplate == null) {
            log.debug("Redis unavailable — skipping lock acquire for key={}", lockKey);
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            boolean ok = Boolean.TRUE.equals(acquired);
            if (!ok) {
                log.info("Slot lock already held: {}", lockKey);
            }
            return ok;
        } catch (Exception e) {
            log.warn("Redis lock acquire failed for key={} — allowing booking. Cause: {}", lockKey, e.getMessage());
            return true;
        }
    }

    public boolean isLocked(String lockKey) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
        } catch (Exception e) {
            log.warn("Redis lock check failed for key={}: {}", lockKey, e.getMessage());
            return false;
        }
    }

    public void release(String lockKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(lockKey);
            log.debug("Released slot lock key={}", lockKey);
        } catch (Exception e) {
            log.warn("Redis lock release failed for key={}: {}", lockKey, e.getMessage());
        }
    }
}
