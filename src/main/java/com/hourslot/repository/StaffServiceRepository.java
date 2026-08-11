package com.hourslot.repository;

import com.hourslot.model.Staff;
import com.hourslot.model.Service;
import com.hourslot.model.Business;
import com.hourslot.model.StaffService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffServiceRepository extends JpaRepository<StaffService, Long> {
    List<StaffService> findByStaff(Staff staff);
    List<StaffService> findByService(Service service);
    List<StaffService> findByStaffBranchBusiness(Business business);
    Optional<StaffService> findByStaffAndService(Staff staff, Service service);
}
