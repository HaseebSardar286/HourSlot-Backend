package com.hourslot.identity.repository;

import com.hourslot.identity.model.CustomerAddress;
import com.hourslot.identity.model.User;
import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CustomerAddressRepository {
    private static final String SELECT = """
            SELECT id, customer_user_id, label, address_line, country_code, region, city, area_name,
                   postal_code, geo_area_id, latitude, longitude, is_default, created_at, updated_at, deleted_at
            FROM customer_addresses
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public CustomerAddressRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public CustomerAddress save(CustomerAddress address) {
        if (address.getId() == null) {
            address.onCreate();
            address.setId(jdbc.insert("""
                    INSERT INTO customer_addresses
                        (customer_user_id, label, address_line, country_code, region, city, area_name, postal_code,
                         geo_area_id, latitude, longitude, is_default, created_at, updated_at)
                    VALUES (:userId, :label, :addressLine, :countryCode, :region, :city, :areaName, :postalCode,
                            :geoAreaId, :latitude, :longitude, :isDefault, :createdAt, :updatedAt)
                    """, bind(address)));
        } else {
            address.onUpdate();
            jdbc.update("""
                    UPDATE customer_addresses SET label = :label, address_line = :addressLine,
                        country_code = :countryCode, region = :region, city = :city, area_name = :areaName,
                        postal_code = :postalCode, geo_area_id = :geoAreaId, latitude = :latitude,
                        longitude = :longitude, is_default = :isDefault, updated_at = :updatedAt
                    WHERE id = :id AND customer_user_id = :userId AND deleted_at IS NULL
                    """, bind(address).addValue("id", address.getId()));
        }
        return address;
    }

    public Optional<CustomerAddress> findByIdAndUser(Long id, User user) {
        return jdbc.findOne(SELECT + """
                 WHERE id = :id AND customer_user_id = :userId AND deleted_at IS NULL
                """, jdbc.params().addValue("id", id).addValue("userId", user.getId()), rows.customerAddress);
    }

    public List<CustomerAddress> findByUser(User user) {
        return jdbc.findList(SELECT + """
                 WHERE customer_user_id = :userId AND deleted_at IS NULL
                 ORDER BY is_default DESC, id
                """, jdbc.params().addValue("userId", user.getId()), rows.customerAddress);
    }

    public void clearDefault(User user) {
        jdbc.update("""
                UPDATE customer_addresses SET is_default = FALSE, updated_at = NOW()
                WHERE customer_user_id = :userId AND deleted_at IS NULL AND is_default = TRUE
                """, jdbc.params().addValue("userId", user.getId()));
    }

    public void delete(CustomerAddress address) {
        jdbc.update("""
                UPDATE customer_addresses SET deleted_at = NOW(), updated_at = NOW(), is_default = FALSE
                WHERE id = :id AND customer_user_id = :userId
                """, jdbc.params().addValue("id", address.getId())
                .addValue("userId", address.getCustomerUser().getId()));
    }

    private MapSqlParameterSource bind(CustomerAddress address) {
        return jdbc.params()
                .addValue("userId", address.getCustomerUser() == null ? null : address.getCustomerUser().getId())
                .addValue("label", address.getLabel())
                .addValue("addressLine", address.getAddressLine())
                .addValue("countryCode", address.getCountryCode())
                .addValue("region", address.getRegion())
                .addValue("city", address.getCity())
                .addValue("areaName", address.getAreaName())
                .addValue("postalCode", address.getPostalCode())
                .addValue("geoAreaId", address.getGeoArea() == null ? null : address.getGeoArea().getId())
                .addValue("latitude", address.getLatitude())
                .addValue("longitude", address.getLongitude())
                .addValue("isDefault", address.isDefault())
                .addValue("createdAt", JdbcSupport.ts(address.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(address.getUpdatedAt()));
    }
}
