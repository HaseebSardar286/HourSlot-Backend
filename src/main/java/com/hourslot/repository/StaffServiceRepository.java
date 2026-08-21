package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Business;
import com.hourslot.model.Service;
import com.hourslot.model.Staff;
import com.hourslot.model.StaffService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class StaffServiceRepository {

    private static final String SELECT = """
            SELECT id, staff_id, service_id, price_override
            FROM staff_services
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffServiceRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<StaffService> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id", jdbc.params().addValue("id", id), rows.staffService);
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
        return jdbc.findList(SELECT + " WHERE staff_id = :staffId",
                jdbc.params().addValue("staffId", staff.getId()), rows.staffService);
    }

    public List<StaffService> findByService(Service service) {
        return jdbc.findList(SELECT + " WHERE service_id = :serviceId",
                jdbc.params().addValue("serviceId", service.getId()), rows.staffService);
    }

    public Optional<StaffService> findByStaffAndService(Staff staff, Service service) {
        return jdbc.findOne(SELECT + " WHERE staff_id = :staffId AND service_id = :serviceId",
                jdbc.params().addValue("staffId", staff.getId()).addValue("serviceId", service.getId()),
                rows.staffService);
    }

    public List<StaffService> findByStaffBranchBusiness(Business business) {
        return jdbc.findList("""
                SELECT ss.id, ss.staff_id, ss.service_id, ss.price_override
                FROM staff_services ss
                JOIN staff s ON s.id = ss.staff_id
                JOIN branches br ON br.id = s.branch_id
                WHERE br.business_id = :bizId
                """, jdbc.params().addValue("bizId", business.getId()), rows.staffService);
    }

    private MapSqlParameterSource bind(StaffService mapping) {
        return jdbc.params()
                .addValue("staffId", mapping.getStaff() == null ? null : mapping.getStaff().getId())
                .addValue("serviceId", mapping.getService() == null ? null : mapping.getService().getId())
                .addValue("priceOverride", mapping.getPriceOverride());
    }
}
