package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.CustomerPackage;
import com.hourslot.model.Service;
import com.hourslot.model.ServicePackage;
import com.hourslot.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Repository
public class CustomerPackageRepository {

    private static final String SELECT = """
            SELECT id, customer_user_id, package_id, business_id, sessions_remaining, expires_at, status,
                   purchase_payment_id, version, created_at, updated_at
            FROM customer_packages
            """;

    private static final String SERVICE_PACKAGE_SELECT = """
            SELECT id, business_id, name, description, price, currency, sessions_count, expiry_days,
                   is_active, created_at, updated_at, deleted_at
            FROM service_packages WHERE id IN (:ids)
            """;

    private static final String BUSINESS_SELECT = """
            SELECT id, organization_id, name, slug, description, status, is_verified, rejection_reason,
                   registration_number, primary_category_id, rating_avg, rating_count, timezone, locale,
                   settings, created_at, updated_at, deleted_at
            FROM businesses WHERE id IN (:ids)
            """;

    private static final String SERVICE_SELECT = """
            SELECT id, business_id, name, description, base_price, currency, duration_minutes, buffer_minutes,
                   max_concurrent, is_active, capacity, is_group_service, sort_order, metadata,
                   created_at, updated_at, deleted_at
            FROM services WHERE id IN (:ids)
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public CustomerPackageRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<CustomerPackage> findById(Long id) {
        Optional<CustomerPackage> found = jdbc.findOne(SELECT + " WHERE id = :id",
                jdbc.params().addValue("id", id), rows.customerPackage);
        found.ifPresent(pkg -> attachDetails(List.of(pkg)));
        return found;
    }

    public List<CustomerPackage> findByCustomerUserOrderByCreatedAtDesc(User customer) {
        List<CustomerPackage> packages = jdbc.findList(
                SELECT + " WHERE customer_user_id = :userId ORDER BY created_at DESC",
                jdbc.params().addValue("userId", customer.getId()),
                rows.customerPackage);
        attachDetails(packages);
        return packages;
    }

    public List<CustomerPackage> findByCustomerUserAndStatus(User customer, String status) {
        List<CustomerPackage> packages = jdbc.findList(
                SELECT + " WHERE customer_user_id = :userId AND status = :status",
                jdbc.params().addValue("userId", customer.getId()).addValue("status", status),
                rows.customerPackage);
        attachDetails(packages);
        return packages;
    }

    public CustomerPackage save(CustomerPackage pkg) {
        if (pkg.getId() == null) {
            pkg.onCreate();
            int version = pkg.getVersion() == null ? 1 : pkg.getVersion();
            pkg.setVersion(version);
            Long id = jdbc.insert("""
                    INSERT INTO customer_packages (
                        customer_user_id, package_id, business_id, sessions_remaining, expires_at, status,
                        purchase_payment_id, version, created_at, updated_at)
                    VALUES (
                        :customerUserId, :packageId, :businessId, :sessionsRemaining, :expiresAt, :status,
                        :purchasePaymentId, :version, :createdAt, :updatedAt)
                    """, bind(pkg));
            pkg.setId(id);
            return pkg;
        }
        pkg.onUpdate();
        jdbc.update("""
                UPDATE customer_packages SET
                    customer_user_id = :customerUserId,
                    package_id = :packageId,
                    business_id = :businessId,
                    sessions_remaining = :sessionsRemaining,
                    expires_at = :expiresAt,
                    status = :status,
                    purchase_payment_id = :purchasePaymentId,
                    version = COALESCE(version, 1) + 1,
                    updated_at = :updatedAt
                WHERE id = :id
                """, bind(pkg).addValue("id", pkg.getId()));
        pkg.setVersion((pkg.getVersion() == null ? 1 : pkg.getVersion()) + 1);
        return pkg;
    }

    private void attachDetails(List<CustomerPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        Set<Long> packageIds = new LinkedHashSet<>();
        Set<Long> businessIds = new LinkedHashSet<>();
        for (CustomerPackage pkg : packages) {
            addId(packageIds, pkg.getServicePackage() == null ? null : pkg.getServicePackage().getId());
            addId(businessIds, pkg.getBusiness() == null ? null : pkg.getBusiness().getId());
        }

        Map<Long, ServicePackage> servicePackages = loadMap(SERVICE_PACKAGE_SELECT, packageIds, rows.servicePackage, ServicePackage::getId);
        for (ServicePackage servicePackage : servicePackages.values()) {
            addId(businessIds, servicePackage.getBusiness() == null ? null : servicePackage.getBusiness().getId());
        }

        Map<Long, List<Long>> serviceIdsByPackage = new LinkedHashMap<>();
        Set<Long> serviceIds = new LinkedHashSet<>();
        if (!packageIds.isEmpty()) {
            jdbc.findList("""
                    SELECT package_id, service_id FROM package_services WHERE package_id IN (:ids)
                    """, jdbc.params().addValue("ids", new ArrayList<>(packageIds)), (rs, i) -> {
                Long packageId = JdbcSupport.getLong(rs, "package_id");
                Long serviceId = JdbcSupport.getLong(rs, "service_id");
                if (packageId != null && serviceId != null) {
                    serviceIdsByPackage.computeIfAbsent(packageId, key -> new ArrayList<>()).add(serviceId);
                    serviceIds.add(serviceId);
                }
                return serviceId;
            });
        }

        Map<Long, Service> services = loadMap(SERVICE_SELECT, serviceIds, rows.service, Service::getId);
        Map<Long, Business> businesses = loadMap(BUSINESS_SELECT, businessIds, rows.business, Business::getId);

        for (ServicePackage servicePackage : servicePackages.values()) {
            List<Service> included = new ArrayList<>();
            List<Long> ids = serviceIdsByPackage.getOrDefault(servicePackage.getId(), List.of());
            for (Long serviceId : ids) {
                Service service = services.get(serviceId);
                if (service != null) {
                    included.add(service);
                }
            }
            servicePackage.setServices(included);
            if (servicePackage.getBusiness() != null) {
                Business business = businesses.get(servicePackage.getBusiness().getId());
                if (business != null) {
                    servicePackage.setBusiness(business);
                }
            }
        }

        for (CustomerPackage pkg : packages) {
            if (pkg.getServicePackage() != null) {
                ServicePackage servicePackage = servicePackages.get(pkg.getServicePackage().getId());
                if (servicePackage != null) {
                    pkg.setServicePackage(servicePackage);
                }
            }
            if (pkg.getBusiness() != null) {
                Business business = businesses.get(pkg.getBusiness().getId());
                if (business != null) {
                    pkg.setBusiness(business);
                }
            } else if (pkg.getServicePackage() != null) {
                pkg.setBusiness(pkg.getServicePackage().getBusiness());
            }
        }
    }

    private MapSqlParameterSource bind(CustomerPackage pkg) {
        return jdbc.params()
                .addValue("customerUserId", pkg.getCustomerUser() == null ? null : pkg.getCustomerUser().getId())
                .addValue("packageId", pkg.getServicePackage() == null ? null : pkg.getServicePackage().getId())
                .addValue("businessId", pkg.getBusiness() == null ? null : pkg.getBusiness().getId())
                .addValue("sessionsRemaining", pkg.getSessionsRemaining())
                .addValue("expiresAt", JdbcSupport.ts(pkg.getExpiresAt()))
                .addValue("status", pkg.getStatus())
                .addValue("purchasePaymentId", pkg.getPurchasePaymentId())
                .addValue("version", pkg.getVersion())
                .addValue("createdAt", JdbcSupport.ts(pkg.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(pkg.getUpdatedAt()));
    }

    private <T> Map<Long, T> loadMap(String sql, Set<Long> ids, RowMapper<T> mapper, Function<T, Long> idFn) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<T> found = jdbc.findList(sql, jdbc.params().addValue("ids", new ArrayList<>(ids)), mapper);
        Map<Long, T> map = new LinkedHashMap<>();
        for (T row : found) {
            Long id = idFn.apply(row);
            if (id != null) {
                map.put(id, row);
            }
        }
        return map;
    }

    private static void addId(Collection<Long> ids, Long id) {
        if (id != null) {
            ids.add(id);
        }
    }
}
