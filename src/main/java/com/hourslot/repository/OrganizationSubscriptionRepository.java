package com.hourslot.repository;

import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Long> {

    @Query("""
            SELECT DISTINCT s FROM OrganizationSubscription s
            JOIN FETCH s.plan p
            LEFT JOIN FETCH p.entitlements
            WHERE s.organization = :organization
              AND s.status IN :statuses
            """)
    List<OrganizationSubscription> findByOrganizationAndStatusIn(
            @Param("organization") Organization organization,
            @Param("statuses") Collection<String> statuses
    );

    default Optional<OrganizationSubscription> findActive(Organization organization) {
        return findByOrganizationAndStatusIn(organization, List.of("ACTIVE", "TRIALING")).stream()
                .max(Comparator.comparing(row -> row.getCreatedAt() == null ? LocalDateTime.MIN : row.getCreatedAt()));
    }
}
