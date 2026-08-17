package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.Favorite;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByCustomerUser(User customerUser);
    boolean existsByCustomerUserAndBusiness(User customerUser, Business business);
    Optional<Favorite> findByCustomerUserAndBusiness(User customerUser, Business business);
}
