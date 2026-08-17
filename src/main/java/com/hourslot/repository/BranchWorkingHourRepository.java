package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.BranchWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchWorkingHourRepository extends JpaRepository<BranchWorkingHour, Long> {
    List<BranchWorkingHour> findByBranchOrderByDayOfWeekAsc(Branch branch);
    List<BranchWorkingHour> findByBranch(Branch branch);
    Optional<BranchWorkingHour> findByBranchAndDayOfWeek(Branch branch, int dayOfWeek);
}
