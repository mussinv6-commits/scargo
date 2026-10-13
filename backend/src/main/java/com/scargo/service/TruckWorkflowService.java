package com.scargo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.entity.GateLog;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.OverloadCheck;
import com.scargo.entity.Truck;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TruckWorkflowService {

    private final TruckRepository truckRepository;
    private final LoadingRecordRepository loadingRecordRepository;
    private final OverloadCheckRepository overloadCheckRepository;
    private final GateLogRepository gateLogRepository;
    private final ObjectMapper objectMapper;

    // 첫 번째 OCR 인식 시 상하차 작업 및 차량 이동 상태 처리
    @Async
    public void processFirstOcr(
            String vehicleNo,
            Long loadingRecordId
    ) {

        try {

            // 첫 OCR 처리 전 PENDING 상태인지 확인
            LoadingRecord loadingRecord =
                    loadingRecordRepository
                            .findById(loadingRecordId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "해당 상하차 기록을 찾을 수 없습니다: "
                                                    + loadingRecordId
                                    )
                            );

            // 이미 시작되거나 종료된 작업이면 중복 실행 방지
            if (loadingRecord.getStatus()
                    != LoadingRecord.LoadingStatus.PENDING) {

                log.warn(
                        "[{}] 첫 OCR 처리 불가: 현재 상하차 상태 = {}",
                        vehicleNo,
                        loadingRecord.getStatus()
                );

                return;
            }

            // 입차 직후 차량 INSIDE 및 작업 IN_PROGRESS
            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            updateLoadingRecordStatus(
                    loadingRecordId,
                    LoadingRecord.LoadingStatus.IN_PROGRESS
            );

            log.info(
                    "[{}] 첫 OCR 완료: OUTSIDE -> INSIDE, "
                            + "PENDING -> IN_PROGRESS",
                    vehicleNo
            );

            // 5초 후 야드 내부 이동
            Thread.sleep(5000);

            updateTruckStatus(
                    vehicleNo,
                    "IN_TRANSIT"
            );

            log.info(
                    "[{}] 5초 경과: INSIDE -> IN_TRANSIT, "
                            + "상하차 IN_PROGRESS 유지",
                    vehicleNo
            );

            // 추가 10초 후 목적지 도착
            Thread.sleep(10000);

            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            log.info(
                    "[{}] 총 15초 경과: IN_TRANSIT -> INSIDE",
                    vehicleNo
            );

            // 목적지 도착 후 상하차 완료
            updateLoadingRecordStatus(
                    loadingRecordId,
                    LoadingRecord.LoadingStatus.COMPLETED
            );

            log.info(
                    "[{}] 상하차 작업 완료: "
                            + "IN_PROGRESS -> COMPLETED",
                    vehicleNo
            );

        } catch (InterruptedException e) {

            log.error(
                    "[{}] 상태 전이 워크플로우 중 인터럽트 발생",
                    vehicleNo,
                    e
            );

            Thread.currentThread().interrupt();
        }
    }

    // 최종 OCR 인식 시 이번 방문의 계량 결과로 출차 여부 결정
    @Transactional
    public void processFinalOcr(String vehicleNo) {

        // 이번 방문의 가장 최근 ENTRY 기록 조회
        Optional<GateLog> entryLog =
                findLatestEntryGateLog(vehicleNo);

        // ENTRY 기록이 없으면 자동 출차 불가
        if (entryLog.isEmpty()) {

            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            log.warn(
                    "[{}] 이번 방문의 ENTRY 기록 없음: "
                            + "자동 출차 불가",
                    vehicleNo
            );

            return;
        }

        Long entryGateLogId =
                entryLog.get().getGateLogId();

        log.info(
                "[{}] 이번 방문 ENTRY gateLogId = {}",
                vehicleNo,
                entryGateLogId
        );

        // 이번 ENTRY에 연결된 계량 결과 조회
        Optional<OverloadCheck> check =
                overloadCheckRepository
                        .findByGateLogId(entryGateLogId);

        // 이번 방문에서 계량하지 않았으면 출차 불가
        if (check.isEmpty()) {

            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            log.warn(
                    "[{}] 이번 방문 계량 기록 없음: "
                            + "자동 출차 불가",
                    vehicleNo
            );

            return;
        }

        OverloadCheck latestCheck =
                check.get();

        // retryCount NULL 방어
        int retryCount =
                latestCheck.getRetryCount() == null
                        ? 0
                        : latestCheck.getRetryCount();

        log.info(
                "[{}] 이번 방문 과적 검사 확인: "
                        + "gateLogId={}, retryCount={}, isPassed={}",
                vehicleNo,
                entryGateLogId,
                retryCount,
                latestCheck.getIsPassed()
        );

        // 재계량 3회 이상이면 관리자 수동 출차 필요
        if (retryCount >= 3) {

            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            log.warn(
                    "[{}] 과적 재계량 {}회: "
                            + "자동 출차 불가, 관리자 수동 출차 필요",
                    vehicleNo,
                    retryCount
            );

            return;
        }

        // 최종 계량 결과가 통과가 아니면 출차 불가
        if (!Boolean.TRUE.equals(
                latestCheck.getIsPassed()
        )) {

            updateTruckStatus(
                    vehicleNo,
                    "INSIDE"
            );

            log.warn(
                    "[{}] 이번 방문 과적 검사 미통과: "
                            + "INSIDE 유지",
                    vehicleNo
            );

            return;
        }

        // 이번 방문 계량 최종 통과 시 자동 출차
        updateTruckStatus(
                vehicleNo,
                "OUTSIDE"
        );

        log.info(
                "[{}] 최종 OCR 및 이번 방문 계량 통과: "
                        + "INSIDE -> OUTSIDE",
                vehicleNo
        );
    }

    // 차량의 최근 OCR 기록 중 가장 최근 ENTRY 조회
    private Optional<GateLog> findLatestEntryGateLog(
            String vehicleNo
    ) {

        List<GateLog> logs =
                gateLogRepository
                        .findTop20ByActualVehicleNoOrderByGateLogIdDesc(
                                vehicleNo
                        );

        return logs.stream()
                .filter(log ->
                        "ENTRY".equals(
                                scanTypeOf(log)
                        )
                )
                .findFirst();
    }

    // GateLog의 ocrRawData에서 scanType 조회
    private String scanTypeOf(GateLog gateLog) {

        String raw =
                gateLog.getOcrRawData();

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {

            JsonNode node =
                    objectMapper
                            .readTree(raw)
                            .path("scanType");

            if (node.isMissingNode()
                    || node.isNull()) {

                return null;
            }

            return node
                    .asText()
                    .trim()
                    .toUpperCase();

        } catch (Exception e) {

            log.warn(
                    "GateLog OCR JSON 파싱 실패: gateLogId={}",
                    gateLog.getGateLogId()
            );

            return null;
        }
    }

    // 차량 상태 변경
    @Transactional
    public void updateTruckStatus(
            String vehicleNo,
            String status
    ) {

        Truck truck =
                truckRepository
                        .findById(vehicleNo)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 차량을 찾을 수 없습니다: "
                                                + vehicleNo
                                )
                        );

        truck.updateStatus(status);

        truckRepository.save(truck);
    }

    // 상하차 작업 상태 변경
    @Transactional
    public void updateLoadingRecordStatus(
            Long loadingRecordId,
            LoadingRecord.LoadingStatus status
    ) {

        LoadingRecord loadingRecord =
                loadingRecordRepository
                        .findById(loadingRecordId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 상하차 기록을 찾을 수 없습니다: "
                                                + loadingRecordId
                                )
                        );

        loadingRecord.updateStatus(status);

        loadingRecordRepository.save(
                loadingRecord
        );
    }
}