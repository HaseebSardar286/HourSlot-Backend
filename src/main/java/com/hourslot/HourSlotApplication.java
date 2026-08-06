package com.hourslot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class HourSlotApplication {
    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                String cleanUri = databaseUrl.replaceFirst("postgres://", "http://").replaceFirst("postgresql://", "http://");
                java.net.URI uri = new java.net.URI(cleanUri);
                
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":");
                    System.setProperty("spring.datasource.username", parts[0]);
                    System.setProperty("spring.datasource.password", parts[1]);
                }
                
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();
                String query = uri.getQuery();
                
                StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
                if (port != -1) {
                    jdbcUrl.append(":").append(port);
                }
                jdbcUrl.append(path);
                if (query != null) {
                    jdbcUrl.append("?").append(query);
                }
                System.setProperty("spring.datasource.url", jdbcUrl.toString());
            } catch (Exception e) {
                System.err.println("Failed to parse DATABASE_URL environment variable: " + e.getMessage());
            }
        }
        SpringApplication.run(HourSlotApplication.class, args);
    }
}
