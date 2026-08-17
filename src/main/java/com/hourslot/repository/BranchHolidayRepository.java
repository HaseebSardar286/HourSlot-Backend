package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.BranchHoliday;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BranchHolidayRepository extends JpaRepository<BranchHoliday, Long> {
    List<BranchHoliday> findByBranchOrderByHolidayDateAsc(Branch branch);
    List<BranchHoliday> findByBranchAndHolidayDate(Branch branch, LocalDate holidayDate);
}
