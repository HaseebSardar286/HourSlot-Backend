package com.hourslot;

import com.hourslot.config.DatabaseUrlParser;
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
public class HourSlotApplication {

    public static void main(String[] args) {
        DatabaseUrlParser.applyFromEnvironment();
        SpringApplication.run(HourSlotApplication.class, args);
    }
}
