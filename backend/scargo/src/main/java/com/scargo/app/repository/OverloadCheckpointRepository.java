package com.scargo.app.repository;

import com.scargo.app.entity.OverloadCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OverloadCheckpointRepository extends JpaRepository<OverloadCheckpoint, Long> {

    // 26.10.28: 활성(is_active=true) 검문소만
    @Query("select c from OverloadCheckpoint c where c.isActive = true and c.latitude between :minLat and :maxLat and c.longitude between :minLng and :maxLng")
    List<OverloadCheckpoint> findByIsActiveTrueAndLatitudeBetweenAndLongitudeBetween(
            @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng);

    @Query("select c from OverloadCheckpoint c where c.isActive = true and (c.checkpointName like %:q% or c.address like %:q%)")
    List<OverloadCheckpoint> findActiveByNameOrAddress(@Param("q") String q);
}
