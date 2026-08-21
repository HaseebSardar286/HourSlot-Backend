package com.hourslot.repository;

import com.hourslot.model.PlanEntitlement;
import com.hourslot.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, Long> {
    List<PlanEntitlement> findByPlan(SubscriptionPlan plan);
}
