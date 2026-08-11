package com.hourslot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SlotLockService {

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
            return true;
        }
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            // If Redis is down, allow booking rather than blocking the marketplace
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
            return false;
        }
    }

    public void release(String lockKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(lockKey);
        } catch (Exception ignored) {
            // no-op
        }
    }
}
