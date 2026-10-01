package com.scargo.repository;

import com.scargo.entity.Container;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContainerRepository extends JpaRepository<Container, String> {

    // 특정 소속 업체의 전체 컨테이너 목록 조회
    List<Container> findByCompanyId(Long companyId);

    //특정 업체의 배정 가능한(미배정된) 컨테이너 목록 조회
    List<Container> findByCompanyIdAndAssignedVehicleNoIsNull(Long companyId);

    //특정 업체의 현재 배정 완료된 컨테이너 목록 조회
    List<Container> findByCompanyIdAndAssignedVehicleNoIsNotNull(Long companyId);

    // 차량 번호로 현재 배정된 컨테이너 조회 (매핑 중복 검사용)
    Optional<Container> findByAssignedVehicleNo(String assignedVehicleNo);

    // 적재 장소별 컨테이너 목록 조회
    List<Container> findByLoadingLocation_LocationId(Long locationId);

    // 특정 규격·종류 코드(예: 45G1)에 해당하는 컨테이너 목록 조회
    List<Container> findByIsoSizeTypeCode(String isoSizeTypeCode);

    // 하이큐브(High Cube) 여부별 조회
    List<Container> findByIsHighCube(Boolean isHighCube);
}