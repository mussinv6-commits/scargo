package com.scargo.repository;

import com.scargo.entity.OverloadCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface OverloadCheckRepository extends JpaRepository<OverloadCheck, Long> {

    // 차량 번호 완전 일치 목록 조회
    List<OverloadCheck> findByVehicleNo(String vehicleNo);

    // 차량 번호 부분 검색 (페이징)
    Page<OverloadCheck> findByVehicleNoContaining(String vehicleNo, Pageable pageable);

    // 컨테이너 번호로 과적 검사 기록 목록 조회
    List<OverloadCheck> findByContainerNo(String containerNo);

    // 규정 위반 여부별 목록 조회 (페이징)
    Page<OverloadCheck> findByIsViolationTrue(Pageable pageable);

    // 최종 통과 여부별 불합격 목록 조회 (페이징)
    Page<OverloadCheck> findByIsPassedFalse(Pageable pageable);

    // 특정 기간 내 과적 검사 이력 조회 (페이징)
    Page<OverloadCheck> findByCheckedAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}