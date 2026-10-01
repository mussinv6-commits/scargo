package com.scargo.repository;

import com.scargo.entity.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GateRepository extends JpaRepository<Gate, Long> {

    // 게이트 코드로 존재 여부 확인 (등록/수정 시 중복 체크용)
    boolean existsByGateCode(String gateCode);

    // 게이트 코드로 게이트 엔티티 조회
    Optional<Gate> findByGateCode(String gateCode);

    // 활성화된(isActive = true) 전체 게이트 목록 조회
    List<Gate> findByIsActiveTrue();

    // 특정 게이트 유형(IN/OUT)별 목록 조회
    List<Gate> findByGateType(String gateType);
}