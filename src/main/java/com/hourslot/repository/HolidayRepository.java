package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Holiday;
import com.hourslot.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByBranch(Branch branch);
    List<Holiday> findByBranchOrderByDateAsc(Branch branch);
    List<Holiday> findByBranchAndStaffIsNullOrderByDateAsc(Branch branch);
    List<Holiday> findByStaff(Staff staff);
    List<Holiday> findByStaffOrderByDateAsc(Staff staff);
    List<Holiday> findByBranchAndDate(Branch branch, LocalDate date);
    List<Holiday> findByStaffAndDate(Staff staff, LocalDate date);
}
