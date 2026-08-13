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

    /**
     * Nearby search using Haversine distance on plain latitude/longitude columns.
     * Radius is in meters. No PostGIS / geometry types required.
     */
    @Query(value = """
            SELECT * FROM branches b
            WHERE b.latitude IS NOT NULL
              AND b.longitude IS NOT NULL
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
    List<Branch> findNearbyBranches(
            @Param("lat") double lat,
            @Param("lon") double lon,
            @Param("radius") double radius
    );
}
