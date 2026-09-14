package com.hourslot.organization.repository;

import com.hourslot.shared.jdbc.JdbcSupport;
import com.hourslot.shared.jdbc.RowMappers;
import com.hourslot.organization.model.PlanEntitlement;
import com.hourslot.organization.model.SubscriptionPlan;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PlanEntitlementRepository {

    private static final String SELECT = """
            SELECT id, plan_id, entitlement_code, value_type, value
            FROM plan_entitlements
            """;

    private final JdbcSupport jdbc;
    private final RowMappers rows;

    public PlanEntitlementRepository(JdbcSupport jdbc, RowMappers rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    public List<PlanEntitlement> findByPlan(SubscriptionPlan plan) {
        return jdbc.findList(SELECT + " WHERE plan_id = :planId",
                jdbc.params().addValue("planId", plan == null ? null : plan.getId()), rows.planEntitlement);
    }
}
