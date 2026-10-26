package com.scargo.app.repository;

import com.scargo.app.entity.TripLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripLocationRepository extends JpaRepository<TripLocation, Long> {

    Optional<TripLocation> findFirstByOrderIdOrderByRecordedAtDesc(Long orderId);

    List<TripLocation> findByOrderIdOrderByRecordedAtAsc(Long orderId);
}
