package com.scargo.app.repository;

import com.scargo.app.entity.Handover;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HandoverRepository extends JpaRepository<Handover, Long> {
    Optional<Handover> findByOrderId(Long orderId);
}
