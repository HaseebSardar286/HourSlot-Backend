package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationSubscription;
import com.hourslot.model.PlanEntitlement;
import com.hourslot.model.SubscriptionPlan;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Repository
public class OrganizationSubscriptionRepository {

    private static final String SELECT_WITH_PLAN = """
            SELECT s.id, s.organization_id, s.plan_id, s.status, s.stripe_subscription_id, s.current_period_start,
                   s.current_period_end, s.cancel_at_period_end, s.entitlement_overrides, s.version, s.created_at,
                   s.updated_at,
                   p.id AS p_id, p.code AS p_code, p.name AS p_name, p.billing_interval AS p_billing_interval,
                   p.price AS p_price, p.currency AS p_currency, p.stripe_price_id AS p_stripe_price_id,
                   p.is_active AS p_is_active, p.sort_order AS p_sort_order, p.features AS p_features
            FROM organization_subscriptions s
            JOIN subscription_plans p ON p.id = s.plan_id
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;
    private final RowMapper<OrganizationSubscription> withPlan;

    public OrganizationSubscriptionRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
        this.withPlan = (rs, i) -> {
            OrganizationSubscription sub = rows.organizationSubscription.mapRow(rs, i);
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setId(JdbcSupport.getLong(rs, "p_id"));
            plan.setCode(rs.getString("p_code"));
            plan.setName(rs.getString("p_name"));
            plan.setBillingInterval(rs.getString("p_billing_interval"));
            plan.setPrice(JdbcSupport.getDecimal(rs, "p_price"));
            plan.setCurrency(rs.getString("p_currency"));
            plan.setStripePriceId(rs.getString("p_stripe_price_id"));
            plan.setActive(rs.getBoolean("p_is_active"));
            Integer sort = JdbcSupport.getInt(rs, "p_sort_order");
            plan.setSortOrder(sort == null ? 0 : sort);
            plan.setFeatures(jdbc.readJsonb(rs, "p_features"));
            sub.setPlan(plan);
            return sub;
        };
    }

    public OrganizationSubscription save(OrganizationSubscription subscription) {
        if (subscription.getId() == null) {
            subscription.onCreate();
            Long id = jdbc.insert("""
                    INSERT INTO organization_subscriptions (organization_id, plan_id, status, stripe_subscription_id,
                                                            current_period_start, current_period_end, cancel_at_period_end,
                                                            entitlement_overrides, version, created_at, updated_at)
                    VALUES (:organizationId, :planId, :status, :stripeSubscriptionId, :currentPeriodStart,
                            :currentPeriodEnd, :cancelAtPeriodEnd, :entitlementOverrides, :version, :createdAt, :updatedAt)
                    """, bind(subscription));
            subscription.setId(id);
            return subscription;
        }
        subscription.onUpdate();
        jdbc.update("""
                UPDATE organization_subscriptions SET organization_id = :organizationId, plan_id = :planId,
                    status = :status, stripe_subscription_id = :stripeSubscriptionId,
                    current_period_start = :currentPeriodStart, current_period_end = :currentPeriodEnd,
                    cancel_at_period_end = :cancelAtPeriodEnd, entitlement_overrides = :entitlementOverrides,
                    updated_at = :updatedAt
                WHERE id = :id
                """, bind(subscription).addValue("id", subscription.getId()));
        return subscription;
    }

    public List<OrganizationSubscription> findByOrganizationAndStatusIn(
            Organization organization, Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        List<OrganizationSubscription> rowsFound = jdbc.findList(SELECT_WITH_PLAN + """
                WHERE s.organization_id = :organizationId AND s.status IN (:statuses)
                ORDER BY s.created_at DESC
                """, jdbc.params()
                .addValue("organizationId", organization == null ? null : organization.getId())
                .addValue("statuses", List.copyOf(statuses)), withPlan);
        rowsFound.forEach(this::attachEntitlements);
        return rowsFound;
    }

    public Optional<OrganizationSubscription> findActive(Organization organization) {
        return findByOrganizationAndStatusIn(organization, List.of("ACTIVE", "TRIALING")).stream()
                .max(Comparator.comparing(row -> row.getCreatedAt() == null ? LocalDateTime.MIN : row.getCreatedAt()));
    }

    private void attachEntitlements(OrganizationSubscription subscription) {
        SubscriptionPlan plan = subscription.getPlan();
        if (plan == null || plan.getId() == null) {
            return;
        }
        List<PlanEntitlement> entitlements = jdbc.findList("""
                SELECT id, plan_id, entitlement_code, value_type, value
                FROM plan_entitlements
                WHERE plan_id = :id
                """, jdbc.params().addValue("id", plan.getId()), rows.planEntitlement);
        plan.setEntitlements(entitlements);
    }

    private MapSqlParameterSource bind(OrganizationSubscription subscription) {
        Integer version = subscription.getVersion();
        if (version == null) {
            version = 1;
        }
        return jdbc.params()
                .addValue("organizationId", subscription.getOrganization() == null ? null : subscription.getOrganization().getId())
                .addValue("planId", subscription.getPlan() == null ? null : subscription.getPlan().getId())
                .addValue("status", subscription.getStatus())
                .addValue("stripeSubscriptionId", subscription.getStripeSubscriptionId())
                .addValue("currentPeriodStart", JdbcSupport.ts(subscription.getCurrentPeriodStart()))
                .addValue("currentPeriodEnd", JdbcSupport.ts(subscription.getCurrentPeriodEnd()))
                .addValue("cancelAtPeriodEnd", subscription.isCancelAtPeriodEnd())
                .addValue("entitlementOverrides", jdbc.jsonb(subscription.getEntitlementOverrides()))
                .addValue("version", version)
                .addValue("createdAt", JdbcSupport.ts(subscription.getCreatedAt()))
                .addValue("updatedAt", JdbcSupport.ts(subscription.getUpdatedAt()));
    }
}
