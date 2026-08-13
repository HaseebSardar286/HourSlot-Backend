package com.hourslot.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Startup schema maintenance that stays under our control (no PostGIS required).
 */
@Component
@Order(1)
public class SchemaCleanupRunner implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(SchemaCleanupRunner.class);

    private final DataSource dataSource;

    public SchemaCleanupRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
            log.info("Ensured users_role_check constraint is removed (if it existed)");

            migrateBranchesAwayFromGeom(stmt);

        } catch (Exception e) {
            log.warn("Schema cleanup encountered an issue: {}", e.getMessage());
        }
    }

    private void migrateBranchesAwayFromGeom(Statement stmt) {
        try {
            ensureColumn(stmt, "branches", "latitude", "DOUBLE PRECISION");
            ensureColumn(stmt, "branches", "longitude", "DOUBLE PRECISION");

            // Copy from PostGIS geom when present; ignore if PostGIS/geom unavailable
            try {
                stmt.execute("""
                        UPDATE branches
                        SET latitude = ST_Y(geom::geometry),
                            longitude = ST_X(geom::geometry)
                        WHERE geom IS NOT NULL
                          AND (latitude IS NULL OR longitude IS NULL)
                        """);
                log.info("Copied existing branch coordinates from geom → latitude/longitude where possible");
            } catch (Exception e) {
                log.debug("geom → lat/lon copy skipped (expected if geom/PostGIS absent): {}", e.getMessage());
            }

            // Drop legacy geom column so the app no longer depends on geometry types
            try {
                stmt.execute("ALTER TABLE branches DROP COLUMN IF EXISTS geom");
                log.info("Dropped legacy branches.geom column (if it existed)");
            } catch (Exception e) {
                log.warn("Could not drop branches.geom: {}", e.getMessage());
            }

            // Safe defaults for any rows still missing coordinates
            stmt.execute("""
                    UPDATE branches
                    SET latitude = 0, longitude = 0
                    WHERE latitude IS NULL OR longitude IS NULL
                    """);

        } catch (Exception e) {
            log.warn("Branch lat/lon migration issue: {}", e.getMessage());
        }
    }

    private void ensureColumn(Statement stmt, String table, String column, String type) throws Exception {
        ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '" + table
                        + "' AND column_name = '" + column + "'"
        );
        boolean exists = rs.next();
        rs.close();
        if (!exists) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            log.info("Added {}.{} ({})", table, column, type);
        }
    }
}
