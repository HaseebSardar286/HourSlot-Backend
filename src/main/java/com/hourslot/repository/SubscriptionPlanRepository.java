package com.hourslot.repository;

import com.hourslot.jdbc.JdbcSupport;
import com.hourslot.jdbc.RowMappers;
import com.hourslot.model.PlanEntitlement;
import com.hourslot.model.SubscriptionPlan;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SubscriptionPlanRepository {

    private static final String SELECT = """
            SELECT id, code, name, billing_interval, price, currency, stripe_price_id, is_active, sort_order, features
            FROM subscription_plans
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public SubscriptionPlanRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public Optional<SubscriptionPlan> findByCode(String code) {
        return jdbc.findOne(SELECT + " WHERE code = :code",
                jdbc.params().addValue("code", code), rows.subscriptionPlan);
    }

    public Optional<SubscriptionPlan> findByCodeWithEntitlements(String code) {
        Optional<SubscriptionPlan> plan = findByCode(code);
        plan.ifPresent(this::loadEntitlements);
        return plan;
    }

    public List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAsc() {
        return jdbc.findList(SELECT + " WHERE is_active = true ORDER BY sort_order ASC",
                jdbc.params(), rows.subscriptionPlan);
    }

    private void loadEntitlements(SubscriptionPlan plan) {
        List<PlanEntitlement> entitlements = jdbc.findList("""
                SELECT id, plan_id, entitlement_code, value_type, value
                FROM plan_entitlements
                WHERE plan_id = :id
                """, jdbc.params().addValue("id", plan.getId()), rows.planEntitlement);
        plan.setEntitlements(entitlements);
    }
}
