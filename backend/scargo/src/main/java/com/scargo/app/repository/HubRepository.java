package com.scargo.app.repository;

import com.scargo.app.entity.Hub;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface HubRepository extends JpaRepository<Hub, Long> {

    java.util.Optional<Hub> findByCenterId(Long centerId); // 26.10.20

    // 26.10.20 좌표는 logistics_centers 에 있음
    @Query("select h from Hub h where h.isActive = true and h.center.latitude between :minLat and :maxLat and h.center.longitude between :minLng and :maxLng")
    List<Hub> findByIsActiveTrueAndLatitudeBetweenAndLongitudeBetween(
            @Param("minLat") BigDecimal minLat, @Param("maxLat") BigDecimal maxLat, @Param("minLng") BigDecimal minLng, @Param("maxLng") BigDecimal maxLng);

    @Query("select h from Hub h where h.isActive = true and (h.hubName like %:q% or h.center.address like %:q%) order by h.isMega desc, h.totalAreaM2 desc")
    List<Hub> search(@Param("q") String q, Pageable pageable);
    java.util.Optional<Hub> findByHubCode(String hubCode); // 26.10.21 v7: AMR(AmrService) 허브 조회 복구
}
