package com.hourslot.repository;

import com.hourslot.model.Business;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessRepository extends JpaRepository<Business, Long> {
    Optional<Business> findByOwner(User owner);
    boolean existsByOwner(User owner);
    Optional<Business> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Business> findByVerified(boolean verified);
}
