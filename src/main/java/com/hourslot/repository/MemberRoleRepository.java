package com.hourslot.repository;

import com.hourslot.model.MemberRole;
import com.hourslot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MemberRoleRepository extends JpaRepository<MemberRole, Long> {

    @Query("""
            SELECT mr FROM MemberRole mr
            JOIN FETCH mr.role r
            JOIN FETCH mr.user u
            WHERE mr.user.id = :userId
              AND (mr.expiresAt IS NULL OR mr.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<MemberRole> findActiveByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT mr FROM MemberRole mr
            JOIN FETCH mr.role r
            JOIN FETCH mr.user u
            WHERE mr.user.id IN :userIds
              AND (mr.expiresAt IS NULL OR mr.expiresAt > CURRENT_TIMESTAMP)
            """)
    List<MemberRole> findActiveByUserIdIn(@Param("userIds") Collection<Long> userIds);

    List<MemberRole> findByUser(User user);
}
