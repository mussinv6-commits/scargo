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

    // 차량 번호 기반 이력 조회
    Page<GateLog> findByActualVehicleNo(
            String actualVehicleNo,
            Pageable pageable
    );

    // 차량 번호 부분 검색
    Page<GateLog> findByActualVehicleNoContaining(
            String actualVehicleNo,
            Pageable pageable
    );

    // OCR 인식 번호판 완전 일치 조회
    Page<GateLog> findByRecognizedPlateNo(
            String recognizedPlateNo,
            Pageable pageable
    );

    // OCR 인식 번호판 부분 검색
    Page<GateLog> findByRecognizedPlateNoContaining(
            String recognizedPlateNo,
            Pageable pageable
    );

    // 게이트 구분별 조회
    Page<GateLog> findByGate_GateType(
            String gateType,
            Pageable pageable
    );

    // OCR 처리 상태별 조회
    Page<GateLog> findByRecognitionStatus(
            String recognitionStatus,
            Pageable pageable
    );

    // 특정 기간 내 통과 이력 조회
    Page<GateLog> findByPassAtBetween(
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    );

    // 특정 게이트명의 특정 기간 통과 이력 조회
    Page<GateLog> findByGate_GateNameAndPassAtBetween(
            String gateName,
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    );

    // 게이트 구분별 특정 기간 통과 이력 조회
    Page<GateLog> findByGate_GateTypeAndPassAtBetween(
            String gateType,
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    );

    // 차량의 최근 OCR 기록 조회
    // TruckWorkflowService에서 가장 최근 ENTRY 기록을 찾을 때 사용
    List<GateLog> findTop20ByActualVehicleNoOrderByGateLogIdDesc(
            String actualVehicleNo
    );

    // 계량 대기열 조회
    // 등록차량으로 매칭된 게이트 기록 중 아직 계량하지 않은 기록만 조회
    @Query(
            "select g from GateLog g join fetch g.gate "
            + "where g.actualVehicleNo is not null "
            + "and (g.passAt >= :since "
            + "or (g.passAt is null and g.createdAt >= :since)) "
            + "and not exists ("
            + "select o.checkId from OverloadCheck o "
            + "where o.gateLogId = g.gateLogId"
            + ") "
            + "order by g.gateLogId asc"
    )
    List<GateLog> findWeighbridgeQueue(
            @Param("since") OffsetDateTime since
    );
}