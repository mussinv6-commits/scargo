package com.scargo.ops.repository;

import com.scargo.ops.entity.HubDock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HubDockRepository extends JpaRepository<HubDock, Long> {
    List<HubDock> findByHubIdOrderByDockCode(Long hubId);
}
