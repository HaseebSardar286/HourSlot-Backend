package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.Customer;
import com.hourslot.model.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByCustomer(Customer customer);
    boolean existsByCustomerAndBusiness(Customer customer, Business business);
    Optional<Favorite> findByCustomerAndBusiness(Customer customer, Business business);
}
