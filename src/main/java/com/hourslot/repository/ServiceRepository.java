package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Service;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public class ServiceRepository {

    private static final String SELECT = """
            SELECT id, business_id, name, description, base_price, currency, duration_minutes, buffer_minutes,
                   max_concurrent, is_active, capacity, is_group_service, sort_order, metadata,
                   created_at, updated_at, deleted_at
            FROM services
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public ServiceRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Service> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.service);
    }

    public List<Service> findAllById(Iterable<Long> ids) {
        List<Long> idList = toIdList(ids);
        if (idList.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT + " WHERE id IN (:ids) AND deleted_at IS NULL",
                jdbc.params().addValue("ids", idList), rows.service);
    }

    public Service save(Service service) {
        if (service.getId() == null) {
            service.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO services (business_id, name, description, base_price, currency, duration_minutes,
                                          buffer_minutes, max_concurrent, is_active, capacity, is_group_service,
                                          sort_order, metadata, created_at, updated_at)
                    VALUES (:businessId, :name, :description, :basePrice, :currency, :durationMinutes,
                            :bufferMinutes, :maxConcurrent, :active, :capacity, :groupService,
                            :sortOrder, :metadata, :createdAt, :updatedAt)
                    """, bind(service));
            service.setId(id);
            return service;
        }
        service.onUpdate();
        jdbc.update("""
                UPDATE services SET business_id = :businessId, name = :name, description = :description,
                    base_price = :basePrice, currency = :currency, duration_minutes = :durationMinutes,
                    buffer_minutes = :bufferMinutes, max_concurrent = :maxConcurrent, is_active = :active,
                    capacity = :capacity, is_group_service = :groupService, sort_order = :sortOrder,
                    metadata = :metadata, updated_at = :updatedAt
                WHERE id = :id
                """, bind(service).addValue("id", service.getId()));
        return service;
    }

    public void delete(Service service) {
        jdbc.update("UPDATE services SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", service.getId()));
    }

    public List<Service> findByBusiness(Business business) {
        return jdbc.findList(SELECT + " WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY sort_order, id",
                jdbc.params().addValue("businessId", business.getId()), rows.service);
    }

    public List<Service> findByBusinessIdIn(Collection<Long> businessIds) {
        if (businessIds == null || businessIds.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT + " WHERE business_id IN (:businessIds) AND deleted_at IS NULL ORDER BY id",
                jdbc.params().addValue("businessIds", businessIds), rows.service);
    }

    private MapSqlParameterSource bind(Service service) {
        return jdbc.params()
                .addValue("businessId", service.getBusiness() == null ? null : service.getBusiness().getId())
                .addValue("name", service.getName())
                .addValue("description", service.getDescription())
                .addValue("basePrice", service.getBasePrice())
                .addValue("currency", service.getCurrency())
                .addValue("durationMinutes", service.getDurationMinutes())
                .addValue("bufferMinutes", service.getBufferMinutes())
                .addValue("maxConcurrent", service.getMaxConcurrent())
                .addValue("active", service.isActive())
                .addValue("capacity", service.getCapacity())
                .addValue("groupService", service.isGroupService())
                .addValue("sortOrder", service.getSortOrder())
                .addValue("metadata", jdbc.jsonb(service.getMetadata()))
                .addValue("createdAt", JdbcSupport.ts(service.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(service.getUpdatedAt()));
    }

    private static List<Long> toIdList(Iterable<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        List<Long> list = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                list.add(id);
            }
        }
        return list;
    }
}
