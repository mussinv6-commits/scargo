package com.scargo.amr.repository;

import com.scargo.amr.entity.HubEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubEventRepository extends JpaRepository<HubEvent, Long> {
    List<HubEvent> findByHubIdOrderByEventIdDesc(Long hubId, Pageable pageable);
}
