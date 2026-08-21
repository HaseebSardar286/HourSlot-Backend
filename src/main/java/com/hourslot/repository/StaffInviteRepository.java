package com.hourslot.repository;

import com.hourslot.model.Organization;
import com.hourslot.model.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffInviteRepository extends JpaRepository<StaffInvite, Long> {
    List<StaffInvite> findByOrganizationOrderByCreatedAtDesc(Organization organization);
    Optional<StaffInvite> findByTokenHash(String tokenHash);
    Optional<StaffInvite> findByIdAndOrganization(Long id, Organization organization);
}
