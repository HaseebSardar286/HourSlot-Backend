package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Organization;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OrganizationRepository {

    private static final String SELECT = """
            SELECT id, name, slug, billing_email, status, stripe_customer_id, stripe_connect_account_id,
                   default_currency, country_code, timezone, created_at, updated_at, deleted_at
            FROM organizations
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public OrganizationRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Organization save(Organization organization) {
        if (organization.getId() == null) {
            organization.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO organizations (name, slug, billing_email, status, stripe_customer_id,
                                               stripe_connect_account_id, default_currency, country_code, timezone,
                                               created_at, updated_at)
                    VALUES (:name, :slug, :billingEmail, :status, :stripeCustomerId, :stripeConnectAccountId,
                            :defaultCurrency, :countryCode, :timezone, :createdAt, :updatedAt)
                    """, bind(organization));
            organization.setId(id);
            return organization;
        }
        organization.onUpdate();
        jdbc.update("""
                UPDATE organizations SET name = :name, slug = :slug, billing_email = :billingEmail, status = :status,
                    stripe_customer_id = :stripeCustomerId, stripe_connect_account_id = :stripeConnectAccountId,
                    default_currency = :defaultCurrency, country_code = :countryCode, timezone = :timezone,
                    updated_at = :updatedAt
                WHERE id = :id
                """, bind(organization).addValue("id", organization.getId()));
        return organization;
    }

    public boolean existsBySlug(String slug) {
        return jdbc.exists("SELECT COUNT(*) FROM organizations WHERE slug = :slug AND deleted_at IS NULL",
                jdbc.params().addValue("slug", slug));
    }

    public Optional<Organization> findBySlug(String slug) {
        return jdbc.findOne(SELECT + " WHERE slug = :slug AND deleted_at IS NULL",
                jdbc.params().addValue("slug", slug), rows.organization);
    }

    private MapSqlParameterSource bind(Organization organization) {
        return jdbc.params()
                .addValue("name", organization.getName())
                .addValue("slug", organization.getSlug())
                .addValue("billingEmail", organization.getBillingEmail())
                .addValue("status", organization.getStatus())
                .addValue("stripeCustomerId", organization.getStripeCustomerId())
                .addValue("stripeConnectAccountId", organization.getStripeConnectAccountId())
                .addValue("defaultCurrency", organization.getDefaultCurrency())
                .addValue("countryCode", organization.getCountryCode())
                .addValue("timezone", organization.getTimezone())
                .addValue("createdAt", JdbcSupport.ts(organization.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(organization.getUpdatedAt()));
    }
}
