package com.hourslot.shared.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads HourSlot-Backend/.env into system properties before Spring Boot starts.
 * Existing environment variables and system properties are not overwritten.
 */
public final class EnvFileLoader {

    private static final Logger log = LogManager.getLogger(EnvFileLoader.class);

    private EnvFileLoader() {
    }

    public static void load() {
        for (Path dir : List.of(Path.of("."), Path.of("HourSlot-Backend"))) {
            Path file = dir.resolve(".env");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Dotenv dotenv = Dotenv.configure()
                    .directory(dir.toAbsolutePath().normalize().toString())
                    .filename(".env")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            int applied = 0;
            for (DotenvEntry entry : dotenv.entries()) {
                if (hasValue(System.getenv(entry.getKey())) || hasValue(System.getProperty(entry.getKey()))) {
                    continue;
                }
                System.setProperty(entry.getKey(), entry.getValue());
                applied++;
            }
            log.info("Loaded {} values from {}", applied, file.toAbsolutePath().normalize());
            return;
        }
        log.warn("No .env file found — copy .env.example to .env and fill in secrets");
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
