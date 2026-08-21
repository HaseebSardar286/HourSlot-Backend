package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.CustomerProfile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CustomerProfileRepository {

    private static final String SELECT = """
            SELECT user_id, date_of_birth, gender, address_line1, address_line2, city, region, postal_code,
                   country_code, metadata, created_at, updated_at, deleted_at
            FROM customer_profiles
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public CustomerProfileRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<CustomerProfile> findById(Long userId) {
        return jdbc.findOne(SELECT + " WHERE user_id = :userId AND deleted_at IS NULL",
                jdbc.params().addValue("userId", userId), rows.customerProfile);
    }

    public CustomerProfile save(CustomerProfile profile) {
        Long userId = userIdOf(profile);
        profile.setUserId(userId);
        boolean exists = jdbc.exists("SELECT COUNT(*) FROM customer_profiles WHERE user_id = :userId",
                jdbc.params().addValue("userId", userId));
        if (!exists) {
            profile.onCreate();
            jdbc.update("""
                    INSERT INTO customer_profiles (user_id, date_of_birth, gender, address_line1, address_line2, city,
                                                   region, postal_code, country_code, metadata, created_at, updated_at)
                    VALUES (:userId, :dateOfBirth, :gender, :addressLine1, :addressLine2, :city, :region, :postalCode,
                            :countryCode, :metadata, :createdAt, :updatedAt)
                    """, bind(profile));
            return profile;
        }
        profile.onUpdate();
        jdbc.update("""
                UPDATE customer_profiles SET date_of_birth = :dateOfBirth, gender = :gender,
                    address_line1 = :addressLine1, address_line2 = :addressLine2, city = :city, region = :region,
                    postal_code = :postalCode, country_code = :countryCode, metadata = :metadata,
                    updated_at = :updatedAt
                WHERE user_id = :userId
                """, bind(profile));
        return profile;
    }

    public void delete(CustomerProfile profile) {
        jdbc.update("UPDATE customer_profiles SET deleted_at = NOW(), updated_at = NOW() WHERE user_id = :userId",
                jdbc.params().addValue("userId", userIdOf(profile)));
    }

    private Long userIdOf(CustomerProfile profile) {
        if (profile.getUserId() != null) {
            return profile.getUserId();
        }
        return profile.getUser() == null ? null : profile.getUser().getId();
    }

    private MapSqlParameterSource bind(CustomerProfile profile) {
        return jdbc.params()
                .addValue("userId", profile.getUserId())
                .addValue("dateOfBirth", profile.getDateOfBirth())
                .addValue("gender", profile.getGender())
                .addValue("addressLine1", profile.getAddressLine1())
                .addValue("addressLine2", profile.getAddressLine2())
                .addValue("city", profile.getCity())
                .addValue("region", profile.getRegion())
                .addValue("postalCode", profile.getPostalCode())
                .addValue("countryCode", profile.getCountryCode())
                .addValue("metadata", jdbc.jsonb(profile.getMetadata()))
                .addValue("createdAt", JdbcSupport.ts(profile.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(profile.getUpdatedAt()));
    }
}
