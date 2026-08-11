package com.hourslot.repository;

import com.hourslot.model.Booking;
import com.hourslot.model.Business;
import com.hourslot.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByBusinessOrderByCreatedAtDesc(Business business);
    boolean existsByBooking(Booking booking);
}
