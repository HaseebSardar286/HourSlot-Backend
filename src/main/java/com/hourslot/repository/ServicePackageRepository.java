package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {
    List<ServicePackage> findByBusiness(Business business);
    List<ServicePackage> findByBusinessAndActiveTrue(Business business);
}
