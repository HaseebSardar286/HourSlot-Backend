package com.duemet;

import com.duemet.shared.config.DatabaseUrlParser;
import com.duemet.shared.config.EnvFileLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        // We use Redis only via StringRedisTemplate (locks/cache), not Redis repositories.
        RedisRepositoriesAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class DuemetApplication {

    public static void main(String[] args) {
        EnvFileLoader.load();
        DatabaseUrlParser.applyFromEnvironment();
        applyAppTimezone();
        SpringApplication.run(DuemetApplication.class, args);
    }

    /**
     * Booking times are wall-clock LocalDateTime values converted to TIMESTAMPTZ via the JVM zone,
     * so pin it explicitly instead of inheriting whatever zone the host happens to run in.
     */
    private static void applyAppTimezone() {
        String zone = System.getenv("APP_TIMEZONE");
        if (zone == null || zone.isBlank()) {
            zone = System.getProperty("APP_TIMEZONE");
        }
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Karachi";
        }
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneId.of(zone.trim())));
    }
}
