package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByBranch(Branch branch);
    Optional<Staff> findByUser(User user);

    @Query("SELECT COUNT(s) FROM Staff s WHERE s.branch.business.organization.id = :orgId")
    long countByOrganizationId(@Param("orgId") Long orgId);
}
