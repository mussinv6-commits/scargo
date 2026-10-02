package com.scargo.service;

import com.scargo.entity.LoadingRecord;
import com.scargo.entity.Truck;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TruckWorkflowService {

    private final TruckRepository truckRepository;
    private final LoadingRecordRepository loadingRecordRepository;
    private final OverloadCheckRepository overloadCheckRepository;

    // 첫 번째 OCR 인식 시 PENDING 작업을 시작하고 차량 상태를 순차적으로 변경
    // 26.10.01 병합: @Transactional 제거 - 15초 동안 하나의 트랜잭션으로 묶이면 중간 상태(INSIDE → IN_TRANSIT → INSIDE)가
    //   DB에 반영되지 않고 마지막에 한꺼번에 저장돼서 화면에서 상태 변화가 보이지 않음.
    //   각 단계의 save()가 바로 커밋되도록 트랜잭션 없이 실행한다.
    @Async
    public void processFirstOcr(String vehicleNo, Long loadingRecordId) {

        try {

            // 첫 OCR 처리 전 상하차 기록이 PENDING 상태인지 확인
            LoadingRecord loadingRecord =
                    loadingRecordRepository.findById(loadingRecordId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "해당 상하차 기록을 찾을 수 없습니다: "
                                                    + loadingRecordId
                                    )
                            );

            // 이미 시작되거나 종료된 작업이면 첫 OCR 워크플로우를 중복 실행하지 않음
            if (loadingRecord.getStatus()
                    != LoadingRecord.LoadingStatus.PENDING) {

                log.warn(
                        "[{}] 첫 OCR 처리 불가: 현재 상하차 상태 = {}",
                        vehicleNo,
                        loadingRecord.getStatus()
                );

                return;
            }

            // 첫 번째 OCR 인식 시 차량 OUTSIDE -> INSIDE, 작업 PENDING -> IN_PROGRESS
            updateTruckStatus(vehicleNo, "INSIDE");

            updateLoadingRecordStatus(
                    loadingRecordId,
                    LoadingRecord.LoadingStatus.IN_PROGRESS
            );

            log.info(
                    "[{}] 첫 OCR 완료: OUTSIDE -> INSIDE, PENDING -> IN_PROGRESS",
                    vehicleNo
            );

            // 5초 후 차량을 야드 내부 이동 상태인 IN_TRANSIT으로 변경
            Thread.sleep(5000);

            updateTruckStatus(vehicleNo, "IN_TRANSIT");

            log.info(
                    "[{}] 5초 경과: INSIDE -> IN_TRANSIT, 상하차 IN_PROGRESS 유지",
                    vehicleNo
            );

            // 추가 10초 후 목적지 도착으로 차량을 다시 INSIDE로 변경
            Thread.sleep(10000);

            updateTruckStatus(vehicleNo, "INSIDE");

            log.info(
                    "[{}] 총 15초 경과: IN_TRANSIT -> INSIDE",
                    vehicleNo
            );

            // 목적지 도착 후 상하차 작업을 먼저 COMPLETED로 변경
            updateLoadingRecordStatus(
                    loadingRecordId,
                    LoadingRecord.LoadingStatus.COMPLETED
            );

            log.info(
                    "[{}] 상하차 작업 완료: IN_PROGRESS -> COMPLETED",
                    vehicleNo
            );

            // 해당 차량의 가장 최근 과적 검사 retryCount 조회
            int retryCount = getRetryCount(vehicleNo);

            log.info(
                    "[{}] 과적 retryCount 확인: {}회",
                    vehicleNo,
                    retryCount
            );

            // retryCount가 3회 이상이면 COMPLETED -> CANCELED 후 차량 INSIDE 유지
            if (retryCount >= 3) {

                updateLoadingRecordStatus(
                        loadingRecordId,
                        LoadingRecord.LoadingStatus.CANCELED
                );

                updateTruckStatus(vehicleNo, "INSIDE");

                log.warn(
                        "[{}] 과적 retryCount {}회: COMPLETED -> CANCELED, 관리자 수동 출차 필요",
                        vehicleNo,
                        retryCount
                );

                return;
            }

            // retryCount가 3회 미만이면 COMPLETED 상태 유지
            log.info(
                    "[{}] 과적 retryCount {}회: COMPLETED 유지",
                    vehicleNo,
                    retryCount
            );

        } catch (InterruptedException e) {

            // 비동기 처리 중 인터럽트 발생 시 현재 스레드의 인터럽트 상태 복원
            log.error(
                    "[{}] 상태 전이 워크플로우 중 인터럽트 발생",
                    vehicleNo,
                    e
            );

            Thread.currentThread().interrupt();
        }
    }

    // 최종 OCR 인식 시 retryCount와 최종 과적 검사 결과를 확인하여 출차 여부 결정
    @Transactional
    public void processFinalOcr(String vehicleNo) {

        // 해당 차량의 가장 최근 과적 검사 retryCount 조회
        int retryCount = getRetryCount(vehicleNo);

        log.info(
                "[{}] 최종 OCR retryCount 확인: {}회",
                vehicleNo,
                retryCount
        );

        // retryCount가 3회 이상이면 자동 출차를 막고 INSIDE 유지
        if (retryCount >= 3) {

            updateTruckStatus(vehicleNo, "INSIDE");

            log.warn(
                    "[{}] 과적 retryCount {}회: 자동 출차 불가, 관리자 수동 출차 필요",
                    vehicleNo,
                    retryCount
            );

            return;
        }

        // retryCount가 3회 미만이면 가장 최근 과적 검사 통과 여부 확인
        boolean isPassed = checkOverweight(vehicleNo);

        // 최종 과적 검사 통과 시 자동 출차
        if (isPassed) {

            updateTruckStatus(vehicleNo, "OUTSIDE");

            log.info(
                    "[{}] 최종 OCR 및 과적 검사 통과: INSIDE -> OUTSIDE",
                    vehicleNo
            );

        } else {

            // 최종 과적 검사 실패 시 차량 INSIDE 유지
            updateTruckStatus(vehicleNo, "INSIDE");

            log.info(
                    "[{}] 최종 과적 검사 미통과: INSIDE 유지",
                    vehicleNo
            );
        }
    }

    // 해당 차량의 가장 최근 과적 검사 기록에서 retryCount 조회
    private int getRetryCount(String vehicleNo) {

        var latestCheck =
                overloadCheckRepository
                        .findTopByVehicleNoOrderByCheckedAtDesc(vehicleNo);

        // 과적 검사 기록이 없으면 retryCount를 0으로 처리
        if (latestCheck.isEmpty()) {

            log.info(
                    "[{}] 과적 검사 기록 없음: retryCount = 0",
                    vehicleNo
            );

            return 0;
        }

        // retryCount가 null이면 0으로 처리
        if (latestCheck.get().getRetryCount() == null) {
            return 0;
        }

        return latestCheck.get().getRetryCount();
    }

    // 차량 상태 변경
    @Transactional
    public void updateTruckStatus(String vehicleNo, String status) {

        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 차량을 찾을 수 없습니다: " + vehicleNo
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
                loadingRecordRepository.findById(loadingRecordId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "해당 상하차 기록을 찾을 수 없습니다: "
                                                + loadingRecordId
                                )
                        );

        loadingRecord.updateStatus(status);

        loadingRecordRepository.save(loadingRecord);
    }

    // 해당 차량의 가장 최근 과적 검사 통과 여부 확인
    private boolean checkOverweight(String vehicleNo) {

        var latestCheck =
                overloadCheckRepository
                        .findTopByVehicleNoOrderByCheckedAtDesc(vehicleNo);

        // 과적 검사 기록이 없으면 기본 통과 처리
        if (latestCheck.isEmpty()) {

            log.info(
                    "[{}] 과적 검사 기록 없음: 기본 통과 처리",
                    vehicleNo
            );

            return true;
        }

        return Boolean.TRUE.equals(
                latestCheck.get().getIsPassed()
        );
    }
}