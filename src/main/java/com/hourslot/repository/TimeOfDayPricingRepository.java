package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.Service;
import com.hourslot.model.TimeOfDayPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TimeOfDayPricingRepository extends JpaRepository<TimeOfDayPricing, Long> {
    List<TimeOfDayPricing> findByService(Service service);

    List<TimeOfDayPricing> findByServiceBusiness(Business business);

    List<TimeOfDayPricing> findByServiceAndDayOfWeek(Service service, int dayOfWeek);
}
