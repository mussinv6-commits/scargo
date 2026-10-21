package com.scargo.amr.repository;

import com.scargo.amr.entity.Hub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HubRepository extends JpaRepository<Hub, Long> {
    Optional<Hub> findByHubCode(String hubCode);
}
