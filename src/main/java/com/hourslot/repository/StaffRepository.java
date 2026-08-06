package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByBranch(Branch branch);
}
