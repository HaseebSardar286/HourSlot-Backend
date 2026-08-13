package com.hourslot.repository;

import com.hourslot.model.Customer;
import com.hourslot.model.CustomerPackage;
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
            WHERE cp.customer = :customer
            ORDER BY cp.createdAt DESC
            """)
    List<CustomerPackage> findByCustomerOrderByCreatedAtDesc(@Param("customer") Customer customer);

    @Query("""
            SELECT DISTINCT cp FROM CustomerPackage cp
            LEFT JOIN FETCH cp.servicePackage sp
            LEFT JOIN FETCH sp.services
            WHERE cp.customer = :customer AND cp.status = :status
            """)
    List<CustomerPackage> findByCustomerAndStatus(@Param("customer") Customer customer, @Param("status") String status);
}
