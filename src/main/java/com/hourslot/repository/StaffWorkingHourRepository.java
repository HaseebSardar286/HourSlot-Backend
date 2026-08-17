package com.hourslot.repository;

import com.hourslot.model.Staff;
import com.hourslot.model.StaffWorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffWorkingHourRepository extends JpaRepository<StaffWorkingHour, Long> {
    List<StaffWorkingHour> findByStaffOrderByDayOfWeekAsc(Staff staff);
    List<StaffWorkingHour> findByStaff(Staff staff);
    Optional<StaffWorkingHour> findByStaffAndDayOfWeek(Staff staff, int dayOfWeek);
}
