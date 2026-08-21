package com.hourslot.repository;

import com.hourslot.model.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    Optional<SubscriptionPlan> findByCode(String code);

    @Query("""
            SELECT DISTINCT p FROM SubscriptionPlan p
            LEFT JOIN FETCH p.entitlements
            WHERE p.code = :code
            """)
    Optional<SubscriptionPlan> findByCodeWithEntitlements(@Param("code") String code);

    List<SubscriptionPlan> findByActiveTrueOrderBySortOrderAsc();
}
