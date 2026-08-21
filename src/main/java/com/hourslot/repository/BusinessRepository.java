package com.hourslot.repository;

import com.hourslot.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Business> findByVerified(boolean verified);

    @Query("""
            SELECT DISTINCT b FROM Business b
            JOIN OrganizationMember om ON om.organization = b.organization
            WHERE om.user.id = :userId AND om.status = 'ACTIVE'
            """)
    Optional<Business> findFirstByMemberUserId(@Param("userId") Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END
            FROM Business b
            JOIN OrganizationMember om ON om.organization = b.organization
            WHERE om.user.id = :userId AND om.status = 'ACTIVE'
            """)
    boolean existsByMemberUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(b) FROM Business b WHERE b.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") Long orgId);
}
