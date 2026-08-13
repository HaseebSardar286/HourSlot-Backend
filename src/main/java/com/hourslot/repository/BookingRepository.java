package com.hourslot.repository;

import com.hourslot.model.Booking;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.Branch;
import com.hourslot.model.Customer;
import com.hourslot.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Query("""
            SELECT DISTINCT b FROM Booking b
            LEFT JOIN FETCH b.customer c
            LEFT JOIN FETCH c.user
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.service
            LEFT JOIN FETCH b.staff
            WHERE b.branch = :branch
            ORDER BY b.bookingTime DESC
            """)
    List<Booking> findByBranchWithDetails(@Param("branch") Branch branch);

    @Query("""
            SELECT DISTINCT b FROM Booking b
            LEFT JOIN FETCH b.customer c
            LEFT JOIN FETCH c.user
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.service
            LEFT JOIN FETCH b.staff
            WHERE b.customer = :customer
            ORDER BY b.bookingTime DESC
            """)
    List<Booking> findByCustomerWithDetails(@Param("customer") Customer customer);

    @Query("""
            SELECT b FROM Booking b
            LEFT JOIN FETCH b.customer c
            LEFT JOIN FETCH c.user
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.service
            LEFT JOIN FETCH b.staff
            LEFT JOIN FETCH b.customerPackage
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    List<Booking> findByBranchOrderByBookingTimeDesc(Branch branch);

    List<Booking> findByCustomerOrderByBookingTimeDesc(Customer customer);
}
