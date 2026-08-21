package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Organization;
import com.hourslot.model.StaffInvite;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class StaffInviteRepository {

    private static final String SELECT = """
            SELECT id, organization_id, business_id, branch_id, email, display_name, designation, token_hash,
                   status, invited_by_user_id, expires_at, accepted_at, created_at
            FROM staff_invites
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public StaffInviteRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public StaffInvite save(StaffInvite invite) {
        if (invite.getId() == null) {
            invite.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO staff_invites (organization_id, business_id, branch_id, email, display_name, designation,
                                               token_hash, status, invited_by_user_id, expires_at, accepted_at, created_at)
                    VALUES (:organizationId, :businessId, :branchId, :email, :displayName, :designation,
                            :tokenHash, :status, :invitedByUserId, :expiresAt, :acceptedAt, :createdAt)
                    """, bind(invite));
            invite.setId(id);
            return invite;
        }
        jdbc.update("""
                UPDATE staff_invites SET organization_id = :organizationId, business_id = :businessId,
                    branch_id = :branchId, email = :email, display_name = :displayName, designation = :designation,
                    token_hash = :tokenHash, status = :status, invited_by_user_id = :invitedByUserId,
                    expires_at = :expiresAt, accepted_at = :acceptedAt
                WHERE id = :id
                """, bind(invite).addValue("id", invite.getId()));
        return invite;
    }

    public List<StaffInvite> findByOrganizationOrderByCreatedAtDesc(Organization organization) {
        return jdbc.findList(SELECT + " WHERE organization_id = :orgId ORDER BY created_at DESC",
                jdbc.params().addValue("orgId", organization.getId()), rows.staffInvite);
    }

    public Optional<StaffInvite> findByTokenHash(String tokenHash) {
        return jdbc.findOne(SELECT + " WHERE token_hash = :tokenHash",
                jdbc.params().addValue("tokenHash", tokenHash), rows.staffInvite);
    }

    public Optional<StaffInvite> findByIdAndOrganization(Long id, Organization organization) {
        return jdbc.findOne(SELECT + " WHERE id = :id AND organization_id = :orgId",
                jdbc.params().addValue("id", id).addValue("orgId", organization.getId()), rows.staffInvite);
    }

    private MapSqlParameterSource bind(StaffInvite invite) {
        return jdbc.params()
                .addValue("organizationId", invite.getOrganization() == null ? null : invite.getOrganization().getId())
                .addValue("businessId", invite.getBusiness() == null ? null : invite.getBusiness().getId())
                .addValue("branchId", invite.getBranch() == null ? null : invite.getBranch().getId())
                .addValue("email", invite.getEmail())
                .addValue("displayName", invite.getDisplayName())
                .addValue("designation", invite.getDesignation())
                .addValue("tokenHash", invite.getTokenHash())
                .addValue("status", invite.getStatus())
                .addValue("invitedByUserId", invite.getInvitedBy() == null ? null : invite.getInvitedBy().getId())
                .addValue("expiresAt", JdbcSupport.ts(invite.getExpiresAt()))
                .addValue("acceptedAt", JdbcSupport.ts(invite.getAcceptedAt()))
                .addValue("createdAt", JdbcSupport.ts(invite.getCreatedAt()));
    }
}
