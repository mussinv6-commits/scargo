package com.scargo.repository;

import com.scargo.entity.GateLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface GateLogRepository extends JpaRepository<GateLog, Long> {

    // 차량 번호 기반 이력 조회 (실제 매칭 번호 완전 일치)
    Page<GateLog> findByActualVehicleNo(String actualVehicleNo, Pageable pageable);

    // 차량 번호 부분 검색 (LIKE %actualVehicleNo% - 현장 키워드 검색용)
    Page<GateLog> findByActualVehicleNoContaining(String actualVehicleNo, Pageable pageable);

    // 인식된 번호판 기반 이력 조회 (OCR 결과 전면 번호판 완전 일치)
    Page<GateLog> findByRecognizedPlateNo(String recognizedPlateNo, Pageable pageable);

    // 인식된 번호판 부분 검색 (LIKE %recognizedPlateNo%)
    Page<GateLog> findByRecognizedPlateNoContaining(String recognizedPlateNo, Pageable pageable);

    // 처리 상태(SUCCESS/FAILED)별 이력 조회
    Page<GateLog> findByRecognitionStatus(String recognitionStatus, Pageable pageable);

    // 특정 기간 내 통과 이력 조회 (통과 일시 passAt 기준)
    Page<GateLog> findByPassAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 2026-09-30 변경: gate_type/gate_name이 GateLog 자체 컬럼에서 게이트
    // 마스터(Gate) 엔티티로 이동함에 따라, Spring Data JPA의 중첩 프로퍼티
    // 탐색(Gate_필드명)을 이용해 조인 쿼리로 대체함(쿼리 문자열을 직접 안 써도
    // 자동 생성됨).

    // 게이트 구분(IN/OUT/BOTH)별 이력 조회 (게이트 마스터의 gate_type 기준)
    Page<GateLog> findByGate_GateType(String gateType, Pageable pageable);

    // 특정 게이트명의 특정 기간 내 통과 이력 조회 (게이트명 + 기간)
    Page<GateLog> findByGate_GateNameAndPassAtBetween(String gateName, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 게이트 구분(IN/OUT/BOTH)별 특정 기간 내 통과 이력 조회 (게이트구분 + 기간)
    Page<GateLog> findByGate_GateTypeAndPassAtBetween(String gateType, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}
