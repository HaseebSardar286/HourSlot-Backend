package com.hourslot.repository;

import com.hourslot.model.CustomerPackage;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerPackageRepository extends JpaRepository<CustomerPackage, Long> {

    @Query("""
            SELECT DISTINCT cp FROM CustomerPackage cp
            LEFT JOIN FETCH cp.servicePackage sp
            LEFT JOIN FETCH sp.business
            LEFT JOIN FETCH sp.services
            WHERE cp.customerUser = :customer
            ORDER BY cp.createdAt DESC
            """)
    List<CustomerPackage> findByCustomerUserOrderByCreatedAtDesc(@Param("customer") User customer);

    @Query("""
            SELECT DISTINCT cp FROM CustomerPackage cp
            LEFT JOIN FETCH cp.servicePackage sp
            LEFT JOIN FETCH sp.services
            WHERE cp.customerUser = :customer AND cp.status = :status
            """)
    List<CustomerPackage> findByCustomerUserAndStatus(@Param("customer") User customer, @Param("status") String status);
}
