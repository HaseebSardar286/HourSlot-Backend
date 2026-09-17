package com.hourslot.request.repository;

import com.hourslot.request.model.ServiceRequest;
import com.hourslot.shared.jdbc.JdbcSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Time;
import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRequestRepository {
    private static final String SELECT = """
            SELECT id, customer_user_id, category_id, service_id, title, description, customer_address_id,
                   country_code, region, city, area_name, geo_area_id, latitude, longitude, preferred_date,
                   preferred_time_from, preferred_time_to, urgency, budget_min, budget_max, currency,
                   status, selected_provider_id, selected_quote_id, booking_id,
                   created_at, updated_at, expires_at, deleted_at
            FROM service_requests
            """;

    private final JdbcSupport jdbc;
    private final RowMapper<ServiceRequest> mapper = (rs, i) -> ServiceRequest.builder()
            .id(rs.getLong("id"))
            .customerUserId(rs.getLong("customer_user_id"))
            .categoryId(JdbcSupport.getLong(rs, "category_id"))
            .serviceId(JdbcSupport.getLong(rs, "service_id"))
            .title(rs.getString("title"))
            .description(rs.getString("description"))
            .customerAddressId(JdbcSupport.getLong(rs, "customer_address_id"))
            .countryCode(rs.getString("country_code"))
            .region(rs.getString("region"))
            .city(rs.getString("city"))
            .areaName(rs.getString("area_name"))
            .geoAreaId(JdbcSupport.getLong(rs, "geo_area_id"))
            .latitude(JdbcSupport.getDouble(rs, "latitude"))
            .longitude(JdbcSupport.getDouble(rs, "longitude"))
            .preferredDate(JdbcSupport.localDate(rs, "preferred_date"))
            .preferredTimeFrom(JdbcSupport.localTime(rs, "preferred_time_from"))
            .preferredTimeTo(JdbcSupport.localTime(rs, "preferred_time_to"))
            .urgency(rs.getString("urgency"))
            .budgetMin(rs.getBigDecimal("budget_min"))
            .budgetMax(rs.getBigDecimal("budget_max"))
            .currency(rs.getString("currency"))
            .status(rs.getString("status"))
            .selectedProviderId(JdbcSupport.getLong(rs, "selected_provider_id"))
            .selectedQuoteId(JdbcSupport.getLong(rs, "selected_quote_id"))
            .bookingId(JdbcSupport.getLong(rs, "booking_id"))
            .createdAt(JdbcSupport.localDateTime(rs, "created_at"))
            .updatedAt(JdbcSupport.localDateTime(rs, "updated_at"))
            .expiresAt(JdbcSupport.localDateTime(rs, "expires_at"))
            .deletedAt(JdbcSupport.localDateTime(rs, "deleted_at"))
            .build();

    public ServiceRequestRepository(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public ServiceRequest save(ServiceRequest request) {
        if (request.getId() == null) {
            request.onCreate();
            request.setId(jdbc.insert("""
                    INSERT INTO service_requests (
                        customer_user_id, category_id, service_id, title, description, customer_address_id,
                        country_code, region, city, area_name, geo_area_id, latitude, longitude, preferred_date,
                        preferred_time_from, preferred_time_to, urgency, budget_min, budget_max, currency,
                        status, selected_provider_id, selected_quote_id, booking_id,
                        created_at, updated_at, expires_at)
                    VALUES (
                        :customerUserId, :categoryId, :serviceId, :title, :description, :customerAddressId,
                        :countryCode, :region, :city, :areaName, :geoAreaId, :latitude, :longitude, :preferredDate,
                        :preferredTimeFrom, :preferredTimeTo, :urgency, :budgetMin, :budgetMax, :currency,
                        :status, :selectedProviderId, :selectedQuoteId, :bookingId,
                        :createdAt, :updatedAt, :expiresAt)
                    """, bind(request)));
        } else {
            request.onUpdate();
            jdbc.update("""
                    UPDATE service_requests SET category_id = :categoryId, service_id = :serviceId,
                        title = :title, description = :description, customer_address_id = :customerAddressId,
                        country_code = :countryCode, region = :region, city = :city, area_name = :areaName,
                        geo_area_id = :geoAreaId, latitude = :latitude, longitude = :longitude,
                        preferred_date = :preferredDate,
                        preferred_time_from = :preferredTimeFrom, preferred_time_to = :preferredTimeTo,
                        urgency = :urgency, budget_min = :budgetMin, budget_max = :budgetMax, currency = :currency,
                        status = :status, selected_provider_id = :selectedProviderId,
                        selected_quote_id = :selectedQuoteId, booking_id = :bookingId,
                        updated_at = :updatedAt, expires_at = :expiresAt
                    WHERE id = :id AND deleted_at IS NULL
                    """, bind(request).addValue("id", request.getId()));
        }
        return request;
    }

    public Optional<ServiceRequest> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), mapper);
    }

    public List<ServiceRequest> findByCustomer(Long customerUserId) {
        return jdbc.findList(SELECT + """
                 WHERE customer_user_id = :customerUserId AND deleted_at IS NULL
                 ORDER BY created_at DESC
                """, jdbc.params().addValue("customerUserId", customerUserId), mapper);
    }

    public int expireOpenPastDue() {
        return jdbc.update("""
                UPDATE service_requests SET status = 'EXPIRED', updated_at = NOW()
                WHERE deleted_at IS NULL
                  AND expires_at IS NOT NULL AND expires_at < NOW()
                  AND UPPER(status) IN ('OPEN', 'MATCHING', 'QUOTED')
                """, jdbc.params());
    }

    public List<ServiceRequest> findExpiredForNotify() {
        return jdbc.findList(SELECT + """
                 WHERE deleted_at IS NULL AND UPPER(status) = 'EXPIRED'
                   AND updated_at > NOW() - INTERVAL '20 minutes'
                 ORDER BY id
                """, jdbc.params(), mapper);
    }

    private MapSqlParameterSource bind(ServiceRequest request) {
        return jdbc.params()
                .addValue("customerUserId", request.getCustomerUserId())
                .addValue("categoryId", request.getCategoryId())
                .addValue("serviceId", request.getServiceId())
                .addValue("title", request.getTitle())
                .addValue("description", request.getDescription())
                .addValue("customerAddressId", request.getCustomerAddressId())
                .addValue("countryCode", request.getCountryCode())
                .addValue("region", request.getRegion())
                .addValue("city", request.getCity())
                .addValue("areaName", request.getAreaName())
                .addValue("geoAreaId", request.getGeoAreaId())
                .addValue("latitude", request.getLatitude())
                .addValue("longitude", request.getLongitude())
                .addValue("preferredDate", request.getPreferredDate() == null ? null : Date.valueOf(request.getPreferredDate()))
                .addValue("preferredTimeFrom", request.getPreferredTimeFrom() == null ? null : Time.valueOf(request.getPreferredTimeFrom()))
                .addValue("preferredTimeTo", request.getPreferredTimeTo() == null ? null : Time.valueOf(request.getPreferredTimeTo()))
                .addValue("urgency", request.getUrgency())
                .addValue("budgetMin", request.getBudgetMin())
                .addValue("budgetMax", request.getBudgetMax())
                .addValue("currency", request.getCurrency())
                .addValue("status", request.getStatus())
                .addValue("selectedProviderId", request.getSelectedProviderId())
                .addValue("selectedQuoteId", request.getSelectedQuoteId())
                .addValue("bookingId", request.getBookingId())
                .addValue("createdAt", JdbcSupport.ts(request.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(request.getUpdatedAt()))
                .addValue("expiresAt", JdbcSupport.ts(request.getExpiresAt()));
    }
}
