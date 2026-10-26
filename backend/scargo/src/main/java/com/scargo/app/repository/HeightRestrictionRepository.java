package com.scargo.app.repository;

import com.scargo.app.entity.HeightRestriction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface HeightRestrictionRepository extends JpaRepository<HeightRestriction, Long> {

    List<HeightRestriction> findByTargetTypeAndTerminalId(String targetType, Long terminalId);

    List<HeightRestriction> findByTargetTypeAndHubId(String targetType, Long hubId);

    List<HeightRestriction> findByTargetTypeAndLatitudeBetweenAndLongitudeBetween(
            String targetType, BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);
}
