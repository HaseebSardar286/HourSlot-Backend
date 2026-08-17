package com.hourslot.repository;

import com.hourslot.model.BusinessMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BusinessMediaRepository extends JpaRepository<BusinessMedia, BusinessMedia.PK> {
    List<BusinessMedia> findByBusinessIdOrderByMediaAsset_SortOrderAsc(Long businessId);

    Optional<BusinessMedia> findFirstByBusinessIdAndRole(Long businessId, String role);

    List<BusinessMedia> findByBusinessIdAndRole(Long businessId, String role);

    @Query("""
            SELECT bm FROM BusinessMedia bm
            JOIN FETCH bm.mediaAsset
            WHERE bm.businessId = :businessId
            """)
    List<BusinessMedia> findWithAssetsByBusinessId(@Param("businessId") Long businessId);
}
