package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByBusiness(Business business);
}
