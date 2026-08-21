package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.MemberRole;
import com.hourslot.model.Role;
import com.hourslot.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public class MemberRoleRepository {

    private static final String SELECT_JOINED = """
            SELECT mr.id, mr.user_id, mr.role_id, mr.organization_id, mr.business_id, mr.branch_id, mr.staff_id,
                   mr.granted_at, mr.granted_by, mr.expires_at, mr.deleted_at,
                   r.id AS r_id, r.scope AS r_scope, r.organization_id AS r_organization_id,
                   r.business_id AS r_business_id, r.code AS r_code, r.name AS r_name,
                   r.is_system AS r_is_system, r.created_at AS r_created_at,
                   u.id AS u_id
            FROM member_roles mr
            JOIN roles r ON r.id = mr.role_id
            JOIN users u ON u.id = mr.user_id
            """;

    private static final String ACTIVE_FILTER = """
            mr.deleted_at IS NULL AND (mr.expires_at IS NULL OR mr.expires_at > NOW())
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;
    private final RowMapper<MemberRole> joined;

    public MemberRoleRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
        this.joined = (rs, i) -> {
            MemberRole memberRole = rows.memberRole.mapRow(rs, i);
            Role role = new Role();
            role.setId(JdbcSupport.getLong(rs, "r_id"));
            role.setScope(rs.getString("r_scope"));
            role.setOrganization(RowMappers.refOrg(JdbcSupport.getLong(rs, "r_organization_id")));
            role.setBusiness(RowMappers.refBusiness(JdbcSupport.getLong(rs, "r_business_id")));
            role.setCode(rs.getString("r_code"));
            role.setName(rs.getString("r_name"));
            role.setSystem(rs.getBoolean("r_is_system"));
            role.setCreatedAt(JdbcSupport.localDateTime(rs, "r_created_at"));
            memberRole.setRole(role);
            User user = new User();
            user.setId(JdbcSupport.getLong(rs, "u_id"));
            memberRole.setUser(user);
            return memberRole;
        };
    }

    public MemberRole save(MemberRole memberRole) {
        if (memberRole.getId() == null) {
            memberRole.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO member_roles (user_id, role_id, organization_id, business_id, branch_id, staff_id,
                                              granted_at, granted_by, expires_at)
                    VALUES (:userId, :roleId, :organizationId, :businessId, :branchId, :staffId, :grantedAt, :grantedBy,
                            :expiresAt)
                    """, bind(memberRole));
            memberRole.setId(id);
            return memberRole;
        }
        jdbc.update("""
                UPDATE member_roles SET user_id = :userId, role_id = :roleId, organization_id = :organizationId,
                    business_id = :businessId, branch_id = :branchId, staff_id = :staffId, granted_at = :grantedAt,
                    granted_by = :grantedBy, expires_at = :expiresAt
                WHERE id = :id
                """, bind(memberRole).addValue("id", memberRole.getId()));
        return memberRole;
    }

    public List<MemberRole> findActiveByUserId(Long userId) {
        return jdbc.findList(SELECT_JOINED + " WHERE mr.user_id = :userId AND " + ACTIVE_FILTER,
                jdbc.params().addValue("userId", userId), joined);
    }

    public List<MemberRole> findActiveByUserIdIn(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return jdbc.findList(SELECT_JOINED + " WHERE mr.user_id IN (:userIds) AND " + ACTIVE_FILTER,
                jdbc.params().addValue("userIds", List.copyOf(userIds)), joined);
    }

    public List<MemberRole> findByUser(User user) {
        return jdbc.findList(SELECT_JOINED + " WHERE mr.user_id = :userId AND " + ACTIVE_FILTER,
                jdbc.params().addValue("userId", user == null ? null : user.getId()), joined);
    }

    private MapSqlParameterSource bind(MemberRole memberRole) {
        return jdbc.params()
                .addValue("userId", memberRole.getUser() == null ? null : memberRole.getUser().getId())
                .addValue("roleId", memberRole.getRole() == null ? null : memberRole.getRole().getId())
                .addValue("organizationId", memberRole.getOrganization() == null ? null : memberRole.getOrganization().getId())
                .addValue("businessId", memberRole.getBusiness() == null ? null : memberRole.getBusiness().getId())
                .addValue("branchId", memberRole.getBranch() == null ? null : memberRole.getBranch().getId())
                .addValue("staffId", memberRole.getStaff() == null ? null : memberRole.getStaff().getId())
                .addValue("grantedAt", JdbcSupport.ts(memberRole.getGrantedAt()))
                .addValue("grantedBy", memberRole.getGrantedBy() == null ? null : memberRole.getGrantedBy().getId())
                .addValue("expiresAt", JdbcSupport.ts(memberRole.getExpiresAt()));
    }
}
