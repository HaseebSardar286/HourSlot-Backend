package com.hourslot.repository;

import com.hourslot.model.Break;
import com.hourslot.model.WorkingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BreakRepository extends JpaRepository<Break, Long> {
    List<Break> findByWorkingHour(WorkingHour workingHour);
}
