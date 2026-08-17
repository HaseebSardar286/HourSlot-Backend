package com.hourslot.repository;

import com.hourslot.model.Booking;
import com.hourslot.model.BookingStatus;
import com.hourslot.model.Branch;
import com.hourslot.model.Staff;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            SELECT DISTINCT b FROM Booking b
            JOIN b.items i
            WHERE i.staff = :staff
              AND b.bookingTime BETWEEN :start AND :end
              AND b.status IN :statuses
            """)
    List<Booking> findByStaffAndBookingTimeBetweenAndStatusIn(
            @Param("staff") Staff staff,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("statuses") List<BookingStatus> statuses
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
            LEFT JOIN FETCH b.customerUser
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.items i
            LEFT JOIN FETCH i.service
            LEFT JOIN FETCH i.staff
            WHERE b.branch = :branch
            ORDER BY b.bookingTime DESC
            """)
    List<Booking> findByBranchWithDetails(@Param("branch") Branch branch);

    @Query("""
            SELECT DISTINCT b FROM Booking b
            LEFT JOIN FETCH b.customerUser
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.items i
            LEFT JOIN FETCH i.service
            LEFT JOIN FETCH i.staff
            WHERE b.customerUser = :customer
            ORDER BY b.bookingTime DESC
            """)
    List<Booking> findByCustomerUserWithDetails(@Param("customer") User customer);

    @Query("""
            SELECT DISTINCT b FROM Booking b
            LEFT JOIN FETCH b.customerUser
            LEFT JOIN FETCH b.branch br
            LEFT JOIN FETCH br.business biz
            LEFT JOIN FETCH b.items i
            LEFT JOIN FETCH i.service
            LEFT JOIN FETCH i.staff
            LEFT JOIN FETCH b.customerPackage
            WHERE b.id = :id
            """)
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    List<Booking> findByBranchOrderByBookingTimeDesc(Branch branch);

    List<Booking> findByCustomerUserOrderByBookingTimeDesc(User customerUser);
}
