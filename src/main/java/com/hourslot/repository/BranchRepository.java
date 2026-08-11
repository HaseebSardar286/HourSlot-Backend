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

    @Query(value = "SELECT * FROM branches WHERE ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography) <= :radius", nativeQuery = true)
    List<Branch> findNearbyBranches(@Param("lat") double lat, @Param("lon") double lon, @Param("radius") double radius);
}
