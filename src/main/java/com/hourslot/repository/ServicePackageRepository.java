package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Service;
import com.hourslot.model.ServicePackage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ServicePackageRepository {

    private static final String SELECT = """
            SELECT id, business_id, name, description, price, currency, sessions_count, expiry_days,
                   is_active, created_at, updated_at, deleted_at
            FROM service_packages
            """;

    private static final String SERVICE_SELECT = """
            SELECT s.id, s.business_id, s.name, s.description, s.base_price, s.currency, s.duration_minutes,
                   s.buffer_minutes, s.max_concurrent, s.is_active, s.capacity, s.is_group_service, s.sort_order,
                   s.metadata, s.created_at, s.updated_at, s.deleted_at
            FROM services s
            JOIN package_services ps ON ps.service_id = s.id
            WHERE ps.package_id = :id AND s.deleted_at IS NULL
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public ServicePackageRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<ServicePackage> findById(Long id) {
        Optional<ServicePackage> found = jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.servicePackage);
        found.ifPresent(this::attachServices);
        return found;
    }

    public ServicePackage save(ServicePackage pkg) {
        if (pkg.getId() == null) {
            pkg.onCreate();
            if (pkg.getUpdatedAt() == null) {
                pkg.setUpdatedAt(pkg.getCreatedAt() != null ? pkg.getCreatedAt() : LocalDateTime.now());
            }
            Long id = jdbc.insert("""
                    INSERT INTO service_packages (business_id, name, description, price, currency, sessions_count,
                                                  expiry_days, is_active, created_at, updated_at)
                    VALUES (:businessId, :name, :description, :price, :currency, :sessionsCount,
                            :expiryDays, :active, :createdAt, :updatedAt)
                    """, bind(pkg));
            pkg.setId(id);
        } else {
            pkg.setUpdatedAt(LocalDateTime.now());
            jdbc.update("""
                    UPDATE service_packages SET business_id = :businessId, name = :name, description = :description,
                        price = :price, currency = :currency, sessions_count = :sessionsCount, expiry_days = :expiryDays,
                        is_active = :active, updated_at = :updatedAt
                    WHERE id = :id
                    """, bind(pkg).addValue("id", pkg.getId()));
        }
        syncServices(pkg);
        return pkg;
    }

    public void delete(ServicePackage pkg) {
        jdbc.update("UPDATE service_packages SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", pkg.getId()));
    }

    public List<ServicePackage> findByBusiness(Business business) {
        List<ServicePackage> list = jdbc.findList(
                SELECT + " WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY id",
                jdbc.params().addValue("businessId", business.getId()), rows.servicePackage);
        list.forEach(this::attachServices);
        return list;
    }

    public List<ServicePackage> findByBusinessAndActiveTrue(Business business) {
        List<ServicePackage> list = jdbc.findList(
                SELECT + " WHERE business_id = :businessId AND is_active = TRUE AND deleted_at IS NULL ORDER BY id",
                jdbc.params().addValue("businessId", business.getId()), rows.servicePackage);
        list.forEach(this::attachServices);
        return list;
    }

    private void attachServices(ServicePackage pkg) {
        List<Service> services = jdbc.findList(SERVICE_SELECT, jdbc.params().addValue("id", pkg.getId()), rows.service);
        pkg.setServices(services);
    }

    private void syncServices(ServicePackage pkg) {
        if (pkg.getServices() == null || pkg.getId() == null) {
            return;
        }
        jdbc.update("DELETE FROM package_services WHERE package_id = :id",
                jdbc.params().addValue("id", pkg.getId()));
        for (Service service : pkg.getServices()) {
            if (service == null || service.getId() == null) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO package_services (package_id, service_id)
                    VALUES (:packageId, :serviceId)
                    """, jdbc.params().addValue("packageId", pkg.getId()).addValue("serviceId", service.getId()));
        }
    }

    private MapSqlParameterSource bind(ServicePackage pkg) {
        return jdbc.params()
                .addValue("businessId", pkg.getBusiness() == null ? null : pkg.getBusiness().getId())
                .addValue("name", pkg.getName())
                .addValue("description", pkg.getDescription())
                .addValue("price", pkg.getPrice())
                .addValue("currency", pkg.getCurrency())
                .addValue("sessionsCount", pkg.getSessionsCount())
                .addValue("expiryDays", pkg.getExpiryDays())
                .addValue("active", pkg.isActive())
                .addValue("createdAt", JdbcSupport.ts(pkg.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(pkg.getUpdatedAt()));
    }
}
