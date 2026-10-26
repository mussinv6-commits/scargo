package com.scargo.app.repository;

import com.scargo.app.entity.OverloadCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface OverloadCheckpointRepository extends JpaRepository<OverloadCheckpoint, Long> {

    List<OverloadCheckpoint> findByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng);

    List<OverloadCheckpoint> findByCheckpointNameContainingOrAddressContaining(String name, String address);
}
