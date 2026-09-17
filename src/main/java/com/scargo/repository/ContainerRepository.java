package com.scargo.repository;

import com.scargo.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContainerRepository extends JpaRepository<Container, String> {

    // 배정 가능한(아직 안 실린) 소속 업체 컨테이너
    List<Container> findByCompanyIdAndAssignedVehicleNoIsNull(Long companyId);

    // 이 차량이 이미 다른 컨테이너에 배정되어 있는지 확인
    Optional<Container> findByAssignedVehicleNo(String vehicleNo);
}