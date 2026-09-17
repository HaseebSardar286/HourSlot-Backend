package com.hourslot.organization.services;

import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {
    private final JdbcSupport jdbc;

    public FeatureFlagService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isEnabled(String code) {
        return jdbc.exists("""
                SELECT COUNT(*) FROM feature_flags
                WHERE code = :code AND is_enabled_global = TRUE
                """, jdbc.params().addValue("code", code));
    }
}
