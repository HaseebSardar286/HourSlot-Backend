package com.hourslot.repository;

import com.hourslot.model.Booking;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import com.hourslot.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    
    List<Booking> findByStaffAndBookingTimeBetweenAndStatusIn(
            Staff staff, 
            LocalDateTime start, 
            LocalDateTime end, 
            List<BookingStatus> statuses
    );

    List<Booking> findByBranchAndBookingTimeBetweenAndStatusIn(
            Branch branch, 
            LocalDateTime start, 
            LocalDateTime end, 
            List<BookingStatus> statuses
    );

    List<Booking> findByBranch(Branch branch);
    List<Booking> findByBranchOrderByBookingTimeDesc(Branch branch);
    List<Booking> findByCustomerOrderByBookingTimeDesc(Customer customer);
}
