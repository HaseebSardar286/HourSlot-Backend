package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Service;
import com.hourslot.model.TimeOfDayPricing;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TimeOfDayPricingRepository {

    private static final String SELECT = """
            SELECT id, service_id, day_of_week, start_time, end_time, price_multiplier, label, is_active
            FROM time_of_day_pricing
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public TimeOfDayPricingRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<TimeOfDayPricing> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.timeOfDayPricing);
    }

    public TimeOfDayPricing save(TimeOfDayPricing pricing) {
        if (pricing.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO time_of_day_pricing (service_id, day_of_week, start_time, end_time, price_multiplier,
                                                     label, is_active)
                    VALUES (:serviceId, :dayOfWeek, :startTime, :endTime, :priceMultiplier, :label, :active)
                    """, bind(pricing));
            pricing.setId(id);
            return pricing;
        }
        jdbc.update("""
                UPDATE time_of_day_pricing SET service_id = :serviceId, day_of_week = :dayOfWeek, start_time = :startTime,
                    end_time = :endTime, price_multiplier = :priceMultiplier, label = :label, is_active = :active
                WHERE id = :id
                """, bind(pricing).addValue("id", pricing.getId()));
        return pricing;
    }

    public void delete(TimeOfDayPricing pricing) {
        jdbc.update("DELETE FROM time_of_day_pricing WHERE id = :id", jdbc.params().addValue("id", pricing.getId()));
    }

    public List<TimeOfDayPricing> findByService(Service service) {
        return jdbc.findList(SELECT + " WHERE service_id = :serviceId ORDER BY day_of_week, start_time",
                jdbc.params().addValue("serviceId", service.getId()), rows.timeOfDayPricing);
    }

    public List<TimeOfDayPricing> findByServiceBusiness(Business business) {
        return jdbc.findList("""
                SELECT t.id, t.service_id, t.day_of_week, t.start_time, t.end_time, t.price_multiplier, t.label, t.is_active
                FROM time_of_day_pricing t
                JOIN services s ON s.id = t.service_id
                WHERE s.business_id = :bizId AND s.deleted_at IS NULL
                ORDER BY t.day_of_week, t.start_time
                """, jdbc.params().addValue("bizId", business.getId()), rows.timeOfDayPricing);
    }

    public List<TimeOfDayPricing> findByServiceAndDayOfWeek(Service service, int dayOfWeek) {
        return jdbc.findList(SELECT + " WHERE service_id = :serviceId AND day_of_week = :dayOfWeek ORDER BY start_time",
                jdbc.params().addValue("serviceId", service.getId()).addValue("dayOfWeek", dayOfWeek),
                rows.timeOfDayPricing);
    }

    private MapSqlParameterSource bind(TimeOfDayPricing pricing) {
        return jdbc.params()
                .addValue("serviceId", pricing.getService() == null ? null : pricing.getService().getId())
                .addValue("dayOfWeek", pricing.getDayOfWeek())
                .addValue("startTime", time(pricing.getStartTime()))
                .addValue("endTime", time(pricing.getEndTime()))
                .addValue("priceMultiplier", pricing.getPriceMultiplier())
                .addValue("label", pricing.getLabel())
                .addValue("active", pricing.isActive());
    }

    private static Time time(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
