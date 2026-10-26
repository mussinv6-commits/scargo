package com.scargo.ops.repository;

import com.scargo.ops.entity.ContainerSeal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ContainerSealRepository extends JpaRepository<ContainerSeal, Long> {
    List<ContainerSeal> findByContainerNoAndStatus(String containerNo, String status);
    List<ContainerSeal> findAllByOrderBySealIdDesc();
}
