package com.scargo.service;

import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.GateLogResponse;
import com.scargo.dto.GateLogUpdateRequest;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.GateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateLogService {

    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository; // 2026-09-30 추가: gateCode -> Gate 조회용

    // 게이트 통과 이력 생성 (OCR 수신 등록)
    @Transactional
    public GateLogResponse createGateLog(GateLogCreateRequest request) {
        Gate gate = gateRepository.findByGateCode(request.getGateCode())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게이트 코드입니다: " + request.getGateCode()));

        GateLog gateLog = GateLog.builder()
                .gate(gate)
                .recognizedPlateNo(request.getRecognizedPlateNo())
                .recognizedTrailerNo(request.getRecognizedTrailerNo())
                .actualVehicleNo(request.getActualVehicleNo())
                .plateConfidence(request.getPlateConfidence())
                .recognitionStatus(request.getRecognitionStatus() != null ? request.getRecognitionStatus() : "SUCCESS")
                .frontImageUrl(request.getFrontImageUrl())
                .rearImageUrl(request.getRearImageUrl())
                .ocrRawData(request.getOcrRawData())
                .vehicleType(request.getVehicleType() != null ? request.getVehicleType() : "UNKNOWN")
                .passAt(request.getPassAt() != null ? request.getPassAt() : OffsetDateTime.now()) // 통과 시각 지정 (추가)
                .build();

        GateLog savedLog = gateLogRepository.save(gateLog);
        return new GateLogResponse(savedLog);
    }

    // 단건 이력 조회
    public GateLogResponse getGateLog(Long gateLogId) {
        GateLog gateLog = gateLogRepository.findById(gateLogId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게이트 통과 이력 ID입니다: " + gateLogId));
        return new GateLogResponse(gateLog);
    }

    // 전체 통과 이력 조회 (페이징)
    public Page<GateLogResponse> getAllGateLogs(Pageable pageable) {
        return gateLogRepository.findAll(pageable)
                .map(GateLogResponse::new);
    }

    // 차량 번호 부분 검색 (실제 매칭 번호 기준, Containing 적용)
    public Page<GateLogResponse> getGateLogsByActualVehicleNo(String actualVehicleNo, Pageable pageable) {
        return gateLogRepository.findByActualVehicleNoContaining(actualVehicleNo, pageable)
                .map(GateLogResponse::new);
    }

    // 게이트 구분(IN/OUT)별 이력 조회 (2026-09-30: 게이트 마스터의 gate_type 기준으로 조회)
    public Page<GateLogResponse> getGateLogsByGateType(String gateType, Pageable pageable) {
        return gateLogRepository.findByGate_GateType(gateType, pageable)
                .map(GateLogResponse::new);
    }

    // 특정 기간 내 통과 이력 조회
    public Page<GateLogResponse> getGateLogsBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        return gateLogRepository.findByPassAtBetween(start, end, pageable)
                .map(GateLogResponse::new);
    }

    // 게이트 이력 수정 (수동 보정 및 차량 매칭 수정)
    @Transactional
    public GateLogResponse updateGateLog(Long gateLogId, GateLogUpdateRequest request) {
        GateLog gateLog = gateLogRepository.findById(gateLogId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게이트 통과 이력 ID입니다: " + gateLogId));

        // 2026-09-30 추가: gateCode가 함께 오면 게이트 마스터를 재조회해서 재배정
        // (gate는 DB 조회가 필요해서 엔티티 내부 update()가 아니라 서비스에서 처리)
        if (request.getGateCode() != null) {
            Gate gate = gateRepository.findByGateCode(request.getGateCode())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게이트 코드입니다: " + request.getGateCode()));
            gateLog.setGate(gate);
        }

        // Entity 내부로 나머지 필드 수정 로직을 위임하여 가독성 개선
        gateLog.update(request);

        return new GateLogResponse(gateLog);
    }

    // 게이트 이력 삭제
    @Transactional
    public void deleteGateLog(Long gateLogId) {
        if (!gateLogRepository.existsById(gateLogId)) {
            throw new IllegalArgumentException("존재하지 않는 게이트 통과 이력 ID입니다: " + gateLogId);
        }
        gateLogRepository.deleteById(gateLogId);
    }
}
