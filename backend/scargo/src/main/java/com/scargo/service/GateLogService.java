package com.scargo.service;

import com.scargo.Enum.NotificationType; // 26.10.01 추가: 미등록 차량 알림
import com.scargo.config.UploadStorage; // 업로드 저장 통일 (형식·이름 검증 + scargo.upload-root)
import com.scargo.controller.TruckOwnership; // 업체 범위 검사
import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.GateLogResponse;
import com.scargo.dto.GateLogUpdateRequest;
import com.scargo.dto.NotificationCreateRequest; // 26.10.01 추가: 미등록 차량 알림
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.entity.LoadingRecord.LoadingStatus; // 26.10.02 추가: ENTRY OCR 최신 PENDING 작업 조회
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.LoadingRecordRepository; // 26.10.02 추가: ENTRY OCR 상하차 기록 조회
import com.scargo.repository.GateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 26.10.01 추가: 알림 실패 로그
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List; // 26.10.01 추가
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateLogService {

    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository;
    private final NotificationService notificationService; // 26.10.01 추가: 미등록 차량 알림 발송

    // 26.10.02 추가: ENTRY OCR 성공 시 최신 PENDING 상하차 기록 조회
    private final LoadingRecordRepository loadingRecordRepository;

    // 26.10.02 추가: ENTRY / EXIT OCR 차량 워크플로우 처리
    private final TruckWorkflowService truckWorkflowService;

    // 26.10.03 2차 추가: EXIT(OUT 게이트) 통과 → 기사 앱 운송오더 GATE_OUT 처리
    private final com.scargo.app.service.TransportOrderService transportOrderService;

    // 업체 소속 차량 확인용 (업체 담당자는 자기 업체 차량의 이력만 조회·수정)
    private final TruckOwnership truckOwnership;

    // 이미지 저장 위치는 설정값 scargo.upload-root 하나만 쓴다 (UploadStorage) - 하위 폴더 gate
    private static final String UPLOAD_SUB = "gate";

    // 게이트 통과 이력 생성 (OCR 수신 등록 + 이미지 파일 저장 포함)
    @Transactional
    public GateLogResponse createGateLog(
            GateLogCreateRequest request,
            MultipartFile frontImage,
            MultipartFile rearImage
    ) {

        // 1. 외래 키로 연결될 Gate 마스터 엔티티 조회 (26.10.01 병합: gateId 또는 gateCode)
        Gate gate = resolveGate(request.getGateId(), request.getGateCode());

        if (gate == null) {
            throw new IllegalArgumentException(
                    "게이트 ID(gateId) 또는 게이트 코드(gateCode) 중 하나는 필수 입력 항목입니다."
            );
        }

        // 2. 파일 저장 처리 및 URL 획득
        String frontImageUrl = saveFile(frontImage);
        String rearImageUrl = saveFile(rearImage);

        // 3. GateLog 엔티티 생성 시 파일 URL 및 데이터 연동
        GateLog gateLog = GateLog.builder()
                .gate(gate)
                .recognizedPlateNo(request.getRecognizedPlateNo())
                .recognizedTrailerNo(request.getRecognizedTrailerNo())
                .actualVehicleNo(request.getActualVehicleNo())
                .plateConfidence(request.getPlateConfidence())
                .recognitionStatus(
                        request.getRecognitionStatus() != null
                                ? request.getRecognitionStatus()
                                : "SUCCESS"
                )
                .frontImageUrl(
                        frontImageUrl != null
                                ? frontImageUrl
                                : request.getFrontImageUrl()
                )
                .rearImageUrl(
                        rearImageUrl != null
                                ? rearImageUrl
                                : request.getRearImageUrl()
                )
                .ocrRawData(request.getOcrRawData()) // 파이썬 원본 JSONB 데이터
                .vehicleType(
                        request.getVehicleType() != null
                                ? request.getVehicleType()
                                : "UNKNOWN"
                )
                .passAt(
                        request.getPassAt() != null
                                ? request.getPassAt()
                                : OffsetDateTime.now()
                )
                .build();

        GateLog savedLog = gateLogRepository.save(gateLog);

        // 4. 26.10.01 추가 - 미등록 차량 알림
        // 번호판은 인식됐는데 등록 차량과 매칭이 안 된 경우
        // 파이썬은 trucks 테이블과 매칭됐을 때만 actualVehicleNo를 채워서 보냄
        boolean unregistered =
                savedLog.getRecognizedPlateNo() != null
                        && !savedLog.getRecognizedPlateNo().isBlank()
                        && (
                                savedLog.getActualVehicleNo() == null
                                        || savedLog.getActualVehicleNo().isBlank()
                        )
                        && "SUCCESS".equals(savedLog.getRecognitionStatus());

        if (unregistered) {
            notifyAdminsUnregisteredVehicle(savedLog);
        }

        // 5. 26.10.02 추가 - ENTRY / EXIT OCR 차량 워크플로우 처리
        if (savedLog.getActualVehicleNo() != null
                && !savedLog.getActualVehicleNo().isBlank()
                && "SUCCESS".equals(savedLog.getRecognitionStatus())) {

            String vehicleNo = savedLog.getActualVehicleNo();

            // ENTRY OCR 성공 → 해당 차량의 최신 PENDING 상하차 작업 시작
            if ("ENTRY".equals(request.getScanType())) {

                loadingRecordRepository
                        .findFirstByTruck_VehicleNoAndStatusOrderByRecordIdDesc(
                                vehicleNo,
                                LoadingStatus.PENDING
                        )
                        .ifPresentOrElse(
                                loadingRecord -> {
                                    log.info(
                                            "[{}] ENTRY OCR 완료 → 상하차 워크플로우 시작 (recordId={})",
                                            vehicleNo,
                                            loadingRecord.getRecordId()
                                    );

                                    truckWorkflowService.processFirstOcr(
                                            vehicleNo,
                                            loadingRecord.getRecordId()
                                    );
                                },
                                () -> log.warn(
                                        "[{}] ENTRY OCR 완료했지만 PENDING 상하차 기록이 없습니다.",
                                        vehicleNo
                                )
                        );
            }

            // EXIT OCR 성공 → 최종 출차 처리
            else if ("EXIT".equals(request.getScanType())) {

                log.info(
                        "[{}] EXIT OCR 게이트 기록 저장 완료 → 최종 출차 처리 시작",
                        vehicleNo
                );

                truckWorkflowService.processFinalOcr(vehicleNo);

                // 26.10.03 2차 추가: 운송오더(출발지·목적지 자동 입력) 연동.
                // 게이트 기록이 커밋된 뒤 별도 트랜잭션으로 처리되므로 여기서 실패해도 게이트 기록은 저장된다.
                transportOrderService.scheduleGateOut(vehicleNo, savedLog.getGateLogId());

                log.info(
                        "[{}] EXIT OCR 최종 출차 처리 완료",
                        vehicleNo
                );
            }
        }

        return new GateLogResponse(savedLog);
    }

    // 26.10.01 추가: 미등록 차량 인식 시 관리자 전원에게 알림 발송
    // 알림은 별도 트랜잭션(REQUIRES_NEW)으로 저장하고, 실패해도 게이트 기록 저장은 그대로 진행
    private void notifyAdminsUnregisteredVehicle(GateLog savedLog) {

        List<Long> adminIds;

        try {

            adminIds = findAdminAccountIds();

        } catch (Exception e) {

            log.warn(
                    "미등록 차량 알림 - 관리자 계정 조회 실패 (gateLogId={}): {}",
                    savedLog.getGateLogId(),
                    e.getMessage()
            );

            return;
        }

        String gateName =
                (savedLog.getGate() != null)
                        ? savedLog.getGate().getGateName()
                        : "게이트";

        for (Long adminId : adminIds) {

            try {

                notificationService.createNotificationInNewTx(
                        NotificationCreateRequest.builder()
                                .accountId(adminId)
                                .title("🚨 미등록 차량 게이트 인식")
                                .message(
                                        "미등록 차량 ["
                                                + savedLog.getRecognizedPlateNo()
                                                + "]이(가) "
                                                + gateName
                                                + "에서 인식되었습니다."
                                )
                                .notificationType(NotificationType.NOTICE)
                                .referenceId(savedLog.getGateLogId())
                                .build()
                );

            } catch (Exception e) {

                // 알림 실패가 게이트 기록 저장을 막으면 안 됨
                log.warn(
                        "미등록 차량 알림 발송 실패 (gateLogId={}, adminId={}): {}",
                        savedLog.getGateLogId(),
                        adminId,
                        e.getMessage()
                );
            }
        }
    }

    // 26.10.01 추가: 알림을 받을 관리자 계정 ID 목록
    // userType = ADMIN 인 계정 전체
    private List<Long> findAdminAccountIds() {

        return notificationService.findAdminAccountIds();
    }

    // 26.10.01 병합: gateId / gateCode 둘 다 지원하는 게이트 조회 헬퍼
    // gateId가 있으면 gateId 우선, 없으면 gateCode로 조회
    private Gate resolveGate(Long gateId, String gateCode) {

        if (gateId != null) {

            return gateRepository
                    .findById(gateId)
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "해당 게이트를 찾을 수 없습니다. ID: " + gateId
                            )
                    );
        }

        if (gateCode != null && !gateCode.isBlank()) {

            return gateRepository
                    .findByGateCode(gateCode)
                    .orElseThrow(() ->
                            new EntityNotFoundException(
                                    "존재하지 않는 게이트 코드입니다: " + gateCode
                            )
                    );
        }

        return null;
    }

    // 파일 공통 저장 헬퍼 메서드
    // 이전: 하드코딩 경로(/data/scargo/images/gate/)에 원래 파일명 그대로 저장 (형식·이름 검증 없음)
    // 지금: UploadStorage 로 저장 - jpg·png·pdf 와 Content-Type 일치 검사, UUID 파일명, 경로는 설정값
    //       (DB 에는 /uploads/gate/<uuid>.<확장자> 로 남고 GET /uploads/** 로 제공됨)
    private String saveFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return null;
        }

        try {

            return UploadStorage.save(UPLOAD_SUB, UUID.randomUUID().toString(), file);

        } catch (IOException e) {

            throw new RuntimeException(
                    "이미지 파일 저장 중 오류가 발생했습니다.",
                    e
            );
        }
    }

    // 단건 이력 조회
    public GateLogResponse getGateLog(Long gateLogId) {

        GateLog gateLog =
                gateLogRepository
                        .findById(gateLogId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "존재하지 않는 게이트 통과 이력 ID입니다: "
                                                + gateLogId
                                )
                        );

        return new GateLogResponse(gateLog);
    }

    // 전체 통과 이력 조회 (페이징)
    public Page<GateLogResponse> getAllGateLogs(Pageable pageable) {

        return gateLogRepository
                .findAll(pageable)
                .map(GateLogResponse::new);
    }

    // 차량 번호 부분 검색
    public Page<GateLogResponse> getGateLogsByActualVehicleNo(
            String actualVehicleNo,
            Pageable pageable
    ) {

        return gateLogRepository
                .findByActualVehicleNoContaining(
                        actualVehicleNo,
                        pageable
                )
                .map(GateLogResponse::new);
    }

    // 게이트 구분(IN/OUT)별 이력 조회
    public Page<GateLogResponse> getGateLogsByGateType(
            String gateType,
            Pageable pageable
    ) {

        return gateLogRepository
                .findByGate_GateType(
                        gateType,
                        pageable
                )
                .map(GateLogResponse::new);
    }

    // 특정 기간 내 통과 이력 조회
    public Page<GateLogResponse> getGateLogsBetween(
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    ) {

        return gateLogRepository
                .findByPassAtBetween(
                        start,
                        end,
                        pageable
                )
                .map(GateLogResponse::new);
    }

    // 게이트 이력 수정
    @Transactional
    public GateLogResponse updateGateLog(
            Long gateLogId,
            GateLogUpdateRequest request
    ) {

        GateLog gateLog =
                gateLogRepository
                        .findById(gateLogId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "존재하지 않는 게이트 통과 이력 ID입니다: "
                                                + gateLogId
                                )
                        );

        // gateId 또는 gateCode가 오면 게이트 재배정
        // 둘 다 없으면 null → 기존 게이트 유지
        Gate newGate =
                resolveGate(
                        request.getGateId(),
                        request.getGateCode()
                );

        gateLog.update(request, newGate);

        return new GateLogResponse(gateLog);
    }

    // 게이트 이력 삭제
    @Transactional
    public void deleteGateLog(Long gateLogId) {

        if (!gateLogRepository.existsById(gateLogId)) {

            throw new IllegalArgumentException(
                    "존재하지 않는 게이트 통과 이력 ID입니다: "
                            + gateLogId
            );
        }

        gateLogRepository.deleteById(gateLogId);
    }

    // ───────── 업체 범위가 있는 조회·수정 (scope == null 이면 관리자: 전체, 값이 있으면 그 업체 소속 차량 이력만) ─────────

    private boolean inScope(GateLog gateLog, Long scope) {
        return scope == null
                || (gateLog.getActualVehicleNo() != null
                && truckOwnership.owns(gateLog.getActualVehicleNo(), scope));
    }

    public GateLogResponse getGateLog(Long gateLogId, Long scope) {

        GateLog gateLog =
                gateLogRepository
                        .findById(gateLogId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "존재하지 않는 게이트 통과 이력 ID입니다: "
                                                + gateLogId
                                )
                        );

        if (!inScope(gateLog, scope)) {
            throw new AccessDeniedException("소속 업체 차량의 이력만 조회할 수 있습니다.");
        }

        return new GateLogResponse(gateLog);
    }

    public Page<GateLogResponse> getAllGateLogs(Pageable pageable, Long scope) {

        if (scope == null) {
            return getAllGateLogs(pageable);
        }

        return gateLogRepository
                .findScoped(scope, pageable)
                .map(GateLogResponse::new);
    }

    public Page<GateLogResponse> getGateLogsByActualVehicleNo(
            String actualVehicleNo,
            Pageable pageable,
            Long scope
    ) {

        if (scope == null) {
            return getGateLogsByActualVehicleNo(actualVehicleNo, pageable);
        }

        return gateLogRepository
                .findScopedByVehicle(scope, actualVehicleNo, pageable)
                .map(GateLogResponse::new);
    }

    public Page<GateLogResponse> getGateLogsByGateType(
            String gateType,
            Pageable pageable,
            Long scope
    ) {

        if (scope == null) {
            return getGateLogsByGateType(gateType, pageable);
        }

        return gateLogRepository
                .findScopedByGateType(scope, gateType, pageable)
                .map(GateLogResponse::new);
    }

    public Page<GateLogResponse> getGateLogsBetween(
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable,
            Long scope
    ) {

        if (scope == null) {
            return getGateLogsBetween(start, end, pageable);
        }

        return gateLogRepository
                .findScopedBetween(scope, start, end, pageable)
                .map(GateLogResponse::new);
    }

    // 수정: 업체 담당자는 자기 업체 차량의 이력만, 차량 번호를 바꿀 때도 자기 업체 차량으로만
    @Transactional
    public GateLogResponse updateGateLog(
            Long gateLogId,
            GateLogUpdateRequest request,
            Long scope
    ) {

        if (scope != null) {

            GateLog gateLog =
                    gateLogRepository
                            .findById(gateLogId)
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "존재하지 않는 게이트 통과 이력 ID입니다: "
                                                    + gateLogId
                                    )
                            );

            if (!inScope(gateLog, scope)) {
                throw new AccessDeniedException("소속 업체 차량의 이력만 수정할 수 있습니다.");
            }

            String newVehicleNo = request.getActualVehicleNo();

            if (newVehicleNo != null && !truckOwnership.owns(newVehicleNo, scope)) {
                throw new AccessDeniedException("소속 업체 차량 번호로만 바꿀 수 있습니다.");
            }
        }

        return updateGateLog(gateLogId, request);
    }
}
