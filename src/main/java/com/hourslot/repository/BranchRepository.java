package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBusiness(Business business);
}
