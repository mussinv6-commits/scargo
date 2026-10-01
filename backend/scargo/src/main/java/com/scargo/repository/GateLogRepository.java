package com.scargo.repository;

import com.scargo.entity.GateLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

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

    // 게이트 구분(IN/OUT)별 이력 조회 (연관된 Gate 엔티티의 gateType 기준)
    Page<GateLog> findByGate_GateType(String gateType, Pageable pageable);

    // 처리 상태(SUCCESS/FAILED)별 이력 조회 
    Page<GateLog> findByRecognitionStatus(String recognitionStatus, Pageable pageable);

    // 특정 기간 내 통과 이력 조회 (통과 일시 passAt 기준)
    Page<GateLog> findByPassAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 특정 게이트명의 특정 기간 내 통과 이력 조회 (연관된 Gate의 gateName + 기간)
    Page<GateLog> findByGate_GateNameAndPassAtBetween(String gateName, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 게이트 구분(IN/OUT)별 특정 기간 내 통과 이력 조회 (연관된 Gate의 gateType + 기간)
    Page<GateLog> findByGate_GateTypeAndPassAtBetween(String gateType, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    // 26.10.01 추가(계중대 정식화): 계량 대기열
    // - 게이트 OCR에서 등록차량과 매칭된(actualVehicleNo 있음) 통과 기록 중
    // - since 이후에 통과했고
    // - 아직 계중대에서 계량(overload_checks.gate_log_id 연결)되지 않은 것만, 먼저 들어온 순서대로
    @Query("select g from GateLog g join fetch g.gate "
         + "where g.actualVehicleNo is not null "
         + "and (g.passAt >= :since or (g.passAt is null and g.createdAt >= :since)) "
         + "and not exists (select o.checkId from OverloadCheck o where o.gateLogId = g.gateLogId) "
         + "order by g.gateLogId asc")
    List<GateLog> findWeighbridgeQueue(@Param("since") OffsetDateTime since);
}
