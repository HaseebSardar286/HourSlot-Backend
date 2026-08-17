package com.hourslot.repository;

import com.hourslot.model.Organization;
import com.hourslot.model.OrganizationMember;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {

    Optional<OrganizationMember> findFirstByUserAndStatus(User user, String status);

    List<OrganizationMember> findByOrganizationAndStatus(Organization organization, String status);

    @Query("""
            SELECT om FROM OrganizationMember om
            JOIN FETCH om.organization
            WHERE om.user.id = :userId AND om.status = 'ACTIVE'
            """)
    List<OrganizationMember> findActiveWithOrgByUserId(@Param("userId") Long userId);
}
