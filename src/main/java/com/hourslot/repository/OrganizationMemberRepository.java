package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationMember;
import com.hourslot.model.User;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrganizationMemberRepository {

    private static final String SELECT = """
            SELECT id, organization_id, user_id, status, invited_by, joined_at, created_at, updated_at, deleted_at
            FROM organization_members
            """;

    private static final String SELECT_WITH_ORG = """
            SELECT om.id, om.organization_id, om.user_id, om.status, om.invited_by, om.joined_at,
                   om.created_at, om.updated_at, om.deleted_at,
                   o.id AS o_id, o.name AS o_name, o.slug AS o_slug, o.billing_email AS o_billing_email,
                   o.status AS o_status, o.stripe_customer_id AS o_stripe_customer_id,
                   o.stripe_connect_account_id AS o_stripe_connect_account_id,
                   o.default_currency AS o_default_currency, o.country_code AS o_country_code,
                   o.timezone AS o_timezone, o.created_at AS o_created_at, o.updated_at AS o_updated_at,
                   o.deleted_at AS o_deleted_at
            FROM organization_members om
            JOIN organizations o ON o.id = om.organization_id
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public OrganizationMemberRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public OrganizationMember save(OrganizationMember member) {
        if (member.getId() == null) {
            member.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO organization_members (organization_id, user_id, status, invited_by, joined_at,
                                                      created_at, updated_at)
                    VALUES (:organizationId, :userId, :status, :invitedBy, :joinedAt, :createdAt, :updatedAt)
                    """, bind(member));
            member.setId(id);
            return member;
        }
        member.onUpdate();
        jdbc.update("""
                UPDATE organization_members SET organization_id = :organizationId, user_id = :userId, status = :status,
                    invited_by = :invitedBy, joined_at = :joinedAt, updated_at = :updatedAt
                WHERE id = :id
                """, bind(member).addValue("id", member.getId()));
        return member;
    }

    public Optional<OrganizationMember> findFirstByUserAndStatus(User user, String status) {
        return jdbc.findOne(SELECT + """
                WHERE user_id = :userId AND status = :status AND deleted_at IS NULL
                ORDER BY id ASC
                LIMIT 1
                """, jdbc.params()
                .addValue("userId", user == null ? null : user.getId())
                .addValue("status", status), rows.organizationMember);
    }

    public List<OrganizationMember> findByOrganizationAndStatus(Organization organization, String status) {
        return jdbc.findList(SELECT + " WHERE organization_id = :organizationId AND status = :status AND deleted_at IS NULL",
                jdbc.params()
                        .addValue("organizationId", organization == null ? null : organization.getId())
                        .addValue("status", status),
                rows.organizationMember);
    }

    public List<OrganizationMember> findActiveWithOrgByUserId(Long userId) {
        return jdbc.findList(SELECT_WITH_ORG + """
                WHERE om.user_id = :userId AND om.status = 'ACTIVE' AND om.deleted_at IS NULL
                  AND o.deleted_at IS NULL
                """, jdbc.params().addValue("userId", userId), (rs, i) -> {
            OrganizationMember member = rows.organizationMember.mapRow(rs, i);
            member.setOrganization(rows.mapOrganization(rs, "o_"));
            return member;
        });
    }

    private MapSqlParameterSource bind(OrganizationMember member) {
        return jdbc.params()
                .addValue("organizationId", member.getOrganization() == null ? null : member.getOrganization().getId())
                .addValue("userId", member.getUser() == null ? null : member.getUser().getId())
                .addValue("status", member.getStatus())
                .addValue("invitedBy", member.getInvitedBy() == null ? null : member.getInvitedBy().getId())
                .addValue("joinedAt", JdbcSupport.ts(member.getJoinedAt()))
                .addValue("createdAt", JdbcSupport.ts(member.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(member.getUpdatedAt()));
    }
}
