package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import com.hourslot.model.WorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WorkingHourRepository extends JpaRepository<WorkingHour, Long> {
    List<WorkingHour> findByBranch(Branch branch);
    List<WorkingHour> findByBranchOrderByDayOfWeekAsc(Branch branch);
    List<WorkingHour> findByBranchAndStaffIsNullOrderByDayOfWeekAsc(Branch branch);
    List<WorkingHour> findByStaff(Staff staff);
    List<WorkingHour> findByStaffOrderByDayOfWeekAsc(Staff staff);
}
