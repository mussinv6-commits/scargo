package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrRobot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AmrRobotRepository extends JpaRepository<AmrRobot, Long> {
    Optional<AmrRobot> findByRobotCode(String robotCode);
    List<AmrRobot> findByHubIdOrderByRobotCodeAsc(Long hubId);
}
