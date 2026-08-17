package com.hourslot.repository;

import com.hourslot.model.Branch;
import com.hourslot.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByBusiness(Business business);

    @Query("""
            SELECT DISTINCT b FROM Branch b
            JOIN FETCH b.business biz
            LEFT JOIN FETCH biz.primaryCategory
            WHERE b.id IN :ids
            """)
    List<Branch> findAllWithBusinessByIdIn(@Param("ids") List<Long> ids);

    @Query("""
            SELECT DISTINCT b FROM Branch b
            JOIN FETCH b.business biz
            LEFT JOIN FETCH biz.primaryCategory
            """)
    List<Branch> findAllWithBusiness();

    /**
     * Nearby search using Haversine distance on plain latitude/longitude columns.
     * Returns branch ids ordered by distance. Radius is in meters.
     */
    @Query(value = """
            SELECT b.id FROM branches b
            INNER JOIN businesses biz ON biz.id = b.business_id
            WHERE b.latitude IS NOT NULL
              AND b.longitude IS NOT NULL
              AND b.is_active = true
              AND b.deleted_at IS NULL
              AND biz.deleted_at IS NULL
              AND biz.status = 'APPROVED'
              AND biz.is_verified = true
              AND (
                6371000 * acos(
                  LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:lat)) * cos(radians(b.latitude))
                      * cos(radians(b.longitude) - radians(:lon))
                    + sin(radians(:lat)) * sin(radians(b.latitude))
                  ))
                )
              ) <= :radius
            ORDER BY (
              6371000 * acos(
                LEAST(1.0, GREATEST(-1.0,
                  cos(radians(:lat)) * cos(radians(b.latitude))
                    * cos(radians(b.longitude) - radians(:lon))
                  + sin(radians(:lat)) * sin(radians(b.latitude))
                ))
              )
            )
            """, nativeQuery = true)
    List<Long> findNearbyBranchIds(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radius") double radius
    );
}
