package com.hourslot.repository;

import com.hourslot.model.Staff;
import com.hourslot.model.StaffTimeOff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StaffTimeOffRepository extends JpaRepository<StaffTimeOff, Long> {
    List<StaffTimeOff> findByStaffOrderByStartAtAsc(Staff staff);

    @Query("""
            SELECT t FROM StaffTimeOff t
            WHERE t.staff = :staff
              AND t.status = 'APPROVED'
              AND t.startAt < :endAt
              AND t.endAt > :startAt
            """)
    List<StaffTimeOff> findOverlapping(
            @Param("staff") Staff staff,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt
    );
}
