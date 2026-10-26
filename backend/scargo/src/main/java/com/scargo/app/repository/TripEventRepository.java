package com.scargo.app.repository;

import com.scargo.app.entity.TripEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface TripEventRepository extends JpaRepository<TripEvent, Long> {

    boolean existsByOrderIdAndEventTypeAndRefId(Long orderId, String eventType, String refId);

    boolean existsByOrderIdAndEventTypeAndOccurredAtAfter(Long orderId, String eventType, OffsetDateTime after);

    long countByOrderIdAndEventType(Long orderId, String eventType);

    List<TripEvent> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
