package com.hourslot.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;

/**
 * Converts Heroku-style DATABASE_URL (postgres://user:pass@host/db)
 * into Spring JDBC properties before the context starts.
 */
public final class DatabaseUrlParser {

    private static final Logger log = LogManager.getLogger(DatabaseUrlParser.class);

    private DatabaseUrlParser() {
    }

    public static void applyFromEnvironment() {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return;
        }

        try {
            String cleanUri = databaseUrl
                    .replaceFirst("postgres://", "http://")
                    .replaceFirst("postgresql://", "http://");
            URI uri = new URI(cleanUri);

            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                int colon = userInfo.indexOf(':');
                System.setProperty("spring.datasource.username", userInfo.substring(0, colon));
                System.setProperty("spring.datasource.password", userInfo.substring(colon + 1));
            }

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
            if (uri.getPort() != -1) {
                jdbcUrl.append(':').append(uri.getPort());
            }
            jdbcUrl.append(uri.getPath());
            if (uri.getQuery() != null) {
                jdbcUrl.append('?').append(uri.getQuery());
            }
            System.setProperty("spring.datasource.url", jdbcUrl.toString());
            log.info("Applied JDBC settings from DATABASE_URL (host={})", uri.getHost());
        } catch (Exception e) {
            log.error("Failed to parse DATABASE_URL: {}", e.getMessage());
        }
    }
}
