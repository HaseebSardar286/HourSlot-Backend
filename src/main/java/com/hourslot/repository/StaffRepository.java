package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class StaffRepository {

    private static final String SELECT = """
            SELECT id, branch_id, user_id, display_name, designation, specialty, bio, rating_avg,
                   is_active, sort_order, created_at, updated_at, deleted_at
            FROM staff
            """;

    private static final String BUSINESS_SELECT = """
            SELECT b.*,
            %s
            FROM businesses b
            """.formatted(RowMappers.BUSINESS_MEDIA_FORMULAS);

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<Staff> findById(Long id) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND deleted_at IS NULL",
                jdbc.params().addValue("id", id), rows.staff);
    }

    public Staff save(Staff staff) {
        if (staff.getId() == null) {
            staff.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO staff (branch_id, user_id, display_name, designation, specialty, bio, rating_avg,
                                       is_active, sort_order, created_at, updated_at)
                    VALUES (:branchId, :userId, :displayName, :designation, :specialty, :bio, :ratingAvg,
                            :active, :sortOrder, :createdAt, :updatedAt)
                    """, bind(staff));
            staff.setId(id);
            return staff;
        }
        staff.onUpdate();
        jdbc.update("""
                UPDATE staff SET branch_id = :branchId, user_id = :userId, display_name = :displayName,
                    designation = :designation, specialty = :specialty, bio = :bio, rating_avg = :ratingAvg,
                    is_active = :active, sort_order = :sortOrder, updated_at = :updatedAt
                WHERE id = :id
                """, bind(staff).addValue("id", staff.getId()));
        return staff;
    }

    public void delete(Staff staff) {
        jdbc.update("UPDATE staff SET deleted_at = NOW(), updated_at = NOW() WHERE id = :id",
                jdbc.params().addValue("id", staff.getId()));
    }

    public List<Staff> findByBranch(Branch branch) {
        return jdbc.findList(SELECT + " WHERE branch_id = :branchId AND deleted_at IS NULL ORDER BY sort_order, id",
                jdbc.params().addValue("branchId", branch.getId()), rows.staff);
    }

    public Optional<Staff> findByUser(User user) {
        Optional<Staff> found = jdbc.findOne(SELECT + " WHERE user_id = :userId AND deleted_at IS NULL",
                jdbc.params().addValue("userId", user.getId()), rows.staff);
        found.ifPresent(this::hydrateBranchBusiness);
        return found;
    }

    public long countByOrganizationId(Long orgId) {
        return jdbc.count("""
                SELECT COUNT(*) FROM staff s
                JOIN branches br ON br.id = s.branch_id
                JOIN businesses biz ON biz.id = br.business_id
                WHERE biz.organization_id = :orgId AND s.deleted_at IS NULL
                """, jdbc.params().addValue("orgId", orgId));
    }

    private void hydrateBranchBusiness(Staff staff) {
        if (staff.getBranch() == null || staff.getBranch().getId() == null) {
            return;
        }
        jdbc.findOne("""
                SELECT id, business_id, name, address, latitude, longitude, phone_number, timezone,
                       is_active, sort_order, created_at, updated_at, deleted_at
                FROM branches WHERE id = :id AND deleted_at IS NULL
                """, jdbc.params().addValue("id", staff.getBranch().getId()), rows.branch)
                .ifPresent(branch -> {
                    staff.setBranch(branch);
                    if (branch.getBusiness() == null || branch.getBusiness().getId() == null) {
                        return;
                    }
                    jdbc.findOne(BUSINESS_SELECT + " WHERE b.id = :id AND b.deleted_at IS NULL",
                            jdbc.params().addValue("id", branch.getBusiness().getId()), rows.business)
                            .ifPresent(branch::setBusiness);
                });
    }

    private MapSqlParameterSource bind(Staff staff) {
        return jdbc.params()
                .addValue("branchId", staff.getBranch() == null ? null : staff.getBranch().getId())
                .addValue("userId", staff.getUser() == null ? null : staff.getUser().getId())
                .addValue("displayName", staff.getDisplayName())
                .addValue("designation", staff.getDesignation())
                .addValue("specialty", staff.getSpecialty())
                .addValue("bio", staff.getBio())
                .addValue("ratingAvg", staff.getRatingAvg())
                .addValue("active", staff.isActive())
                .addValue("sortOrder", staff.getSortOrder())
                .addValue("createdAt", JdbcSupport.ts(staff.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(staff.getUpdatedAt()));
    }
}
