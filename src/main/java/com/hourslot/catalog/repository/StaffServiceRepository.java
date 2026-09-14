package com.hourslot.catalog.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import com.hourslot.organization.model.Branch;
import com.hourslot.organization.model.Business;
import com.hourslot.catalog.model.Service;
import com.hourslot.organization.model.Staff;
import com.hourslot.catalog.model.StaffService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@Repository
public class StaffServiceRepository {

    private static final String SELECT = """
            SELECT id, staff_id, service_id, price_override
            FROM staff_services
            """;

    private static final String STAFF_SELECT = """
            SELECT id, branch_id, user_id, display_name, designation, specialty, bio, rating_avg,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM staff WHERE id IN (:ids) AND deleted_at IS NULL
            """;

    private static final String BRANCH_SELECT = """
            SELECT id, business_id, name, address, latitude, longitude, phone_number, timezone,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM branches WHERE id IN (:ids) AND deleted_at IS NULL
            """;

    private static final String SERVICE_SELECT = """
            SELECT id, business_id, name, description, base_price, currency, duration_minutes, buffer_minutes,
                   max_concurrent, is_active, capacity, is_group_service, sort_order, metadata,
                   created_at, updated_at, deleted_at
            FROM services WHERE id IN (:ids)
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffServiceRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<StaffService> findById(Long id) {
        Optional<StaffService> found = jdbc.findOne(SELECT + " WHERE id = :id",
                jdbc.params().addValue("id", id), rows.staffService);
        found.ifPresent(row -> attachDetails(List.of(row)));
        return found;
    }

    public StaffService save(StaffService mapping) {
        if (mapping.getId() == null) {
            Long id = jdbc.insert("""
                    INSERT INTO staff_services (staff_id, service_id, price_override)
                    VALUES (:staffId, :serviceId, :priceOverride)
                    """, bind(mapping));
            mapping.setId(id);
            return mapping;
        }
        jdbc.update("""
                UPDATE staff_services SET staff_id = :staffId, service_id = :serviceId, price_override = :priceOverride
                WHERE id = :id
                """, bind(mapping).addValue("id", mapping.getId()));
        return mapping;
    }

    public void delete(StaffService mapping) {
        jdbc.update("DELETE FROM staff_services WHERE id = :id", jdbc.params().addValue("id", mapping.getId()));
    }

    public List<StaffService> findByStaff(Staff staff) {
        List<StaffService> list = jdbc.findList(SELECT + " WHERE staff_id = :staffId",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffService);
        attachDetails(list);
        return list;
    }

    public List<StaffService> findByService(Service service) {
        List<StaffService> list = jdbc.findList(SELECT + " WHERE service_id = :serviceId",
                jdbc.params().addValue("serviceId", service.getId()), rows.staffService);
        attachDetails(list);
        return list;
    }

    public Optional<StaffService> findByStaffAndService(Staff staff, Service service) {
        Optional<StaffService> found = jdbc.findOne(
                SELECT + " WHERE staff_id = :staffId AND service_id = :serviceId",
                jdbc.params().addValue("staffId", staff.getId()).addValue("serviceId", service.getId()),
                rows.staffService);
        found.ifPresent(row -> attachDetails(List.of(row)));
        return found;
    }

    public List<StaffService> findByStaffBranchBusiness(Business business) {
        List<StaffService> list = jdbc.findList("""
                SELECT ss.id, ss.staff_id, ss.service_id, ss.price_override
                FROM staff_services ss
                JOIN staff s ON s.id = ss.staff_id
                JOIN branches br ON br.id = s.branch_id
                WHERE br.business_id = :bizId
                  AND s.deleted_at IS NULL
                ORDER BY ss.service_id, s.display_name, ss.id
                """, jdbc.params().addValue("bizId", business.getId()), rows.staffService);
        attachDetails(list);
        return list;
    }

    private void attachDetails(List<StaffService> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        Set<Long> staffIds = new LinkedHashSet<>();
        Set<Long> serviceIds = new LinkedHashSet<>();
        for (StaffService mapping : mappings) {
            if (mapping.getStaff() != null && mapping.getStaff().getId() != null) {
                staffIds.add(mapping.getStaff().getId());
            }
            if (mapping.getService() != null && mapping.getService().getId() != null) {
                serviceIds.add(mapping.getService().getId());
            }
        }
        Map<Long, Staff> staff = loadMap(STAFF_SELECT, staffIds, rows.staff, Staff::getId);
        Set<Long> branchIds = new LinkedHashSet<>();
        for (Staff member : staff.values()) {
            if (member.getBranch() != null && member.getBranch().getId() != null) {
                branchIds.add(member.getBranch().getId());
            }
        }
        Map<Long, Branch> branches = loadMap(BRANCH_SELECT, branchIds, rows.branch, Branch::getId);
        for (Staff member : staff.values()) {
            if (member.getBranch() != null && member.getBranch().getId() != null) {
                Branch branch = branches.get(member.getBranch().getId());
                if (branch != null) {
                    member.setBranch(branch);
                }
            }
        }
        Map<Long, Service> services = loadMap(SERVICE_SELECT, serviceIds, rows.service, Service::getId);
        for (StaffService mapping : mappings) {
            if (mapping.getStaff() != null) {
                Staff member = staff.get(mapping.getStaff().getId());
                if (member != null) {
                    mapping.setStaff(member);
                }
            }
            if (mapping.getService() != null) {
                Service service = services.get(mapping.getService().getId());
                if (service != null) {
                    mapping.setService(service);
                }
            }
        }
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

    private MapSqlParameterSource bind(StaffService mapping) {
        return jdbc.params()
                .addValue("staffId", mapping.getStaff() == null ? null : mapping.getStaff().getId())
                .addValue("serviceId", mapping.getService() == null ? null : mapping.getService().getId())
                .addValue("priceOverride", mapping.getPriceOverride());
    }
}
