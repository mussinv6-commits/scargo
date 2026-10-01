package com.scargo.service;

import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.GateLogResponse;
import com.scargo.dto.GateLogUpdateRequest;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.GateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateLogService {

    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository;

    // 파일 저장 경로 설정 (서버 환경에 맞게 변경 가능)
    private static final String UPLOAD_DIR = "/data/scargo/images/gate/";

    // 게이트 통과 이력 생성 (OCR 수신 등록 + 이미지 파일 저장 포함)
    @Transactional
    public GateLogResponse createGateLog(GateLogCreateRequest request, 
                                           MultipartFile frontImage, 
                                           MultipartFile rearImage) {
        
        // 1. 외래 키로 연결될 Gate 마스터 엔티티 조회 (26.10.01 병합: gateId 또는 gateCode)
        Gate gate = resolveGate(request.getGateId(), request.getGateCode());
        if (gate == null) {
            throw new IllegalArgumentException("게이트 ID(gateId) 또는 게이트 코드(gateCode) 중 하나는 필수 입력 항목입니다.");
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
                .recognitionStatus(request.getRecognitionStatus() != null ? request.getRecognitionStatus() : "SUCCESS")
                .frontImageUrl(frontImageUrl != null ? frontImageUrl : request.getFrontImageUrl())
                .rearImageUrl(rearImageUrl != null ? rearImageUrl : request.getRearImageUrl())
                .ocrRawData(request.getOcrRawData()) // 파이썬 원본 JSONB 데이터
                .vehicleType(request.getVehicleType() != null ? request.getVehicleType() : "UNKNOWN")
                .passAt(request.getPassAt() != null ? request.getPassAt() : OffsetDateTime.now())
                .build();

        GateLog savedLog = gateLogRepository.save(gateLog);
        return new GateLogResponse(savedLog);
    }

    // 26.10.01 병합: gateId(태수님) / gateCode(길웅님) 둘 다 지원하는 게이트 조회 헬퍼
    // - gateId가 있으면 gateId 우선, 없으면 gateCode로 조회, 둘 다 없으면 null 반환
    private Gate resolveGate(Long gateId, String gateCode) {
        if (gateId != null) {
            return gateRepository.findById(gateId)
                    .orElseThrow(() -> new EntityNotFoundException("해당 게이트를 찾을 수 없습니다. ID: " + gateId));
        }
        if (gateCode != null && !gateCode.isBlank()) {
            return gateRepository.findByGateCode(gateCode)
                    .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 게이트 코드입니다: " + gateCode));
        }
        return null;
    }

    // 파일 공통 저장 헬퍼 메서드
    private String saveFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        
        try {
            File dir = new File(UPLOAD_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String savedFileName = UUID.randomUUID().toString() + "_" + originalFilename;
            File targetFile = new File(UPLOAD_DIR + savedFileName);
            
            file.transferTo(targetFile);
            
            return "/images/gate/" + savedFileName; // DB에 저장할 웹 접근 경로
        } catch (IOException e) {
            throw new RuntimeException("이미지 파일 저장 중 오류가 발생했습니다.", e);
        }
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

    // 차량 번호 부분 검색
    public Page<GateLogResponse> getGateLogsByActualVehicleNo(String actualVehicleNo, Pageable pageable) {
        return gateLogRepository.findByActualVehicleNoContaining(actualVehicleNo, pageable)
                .map(GateLogResponse::new);
    }

    // 게이트 구분(IN/OUT)별 이력 조회
    public Page<GateLogResponse> getGateLogsByGateType(String gateType, Pageable pageable) {
        return gateLogRepository.findByGate_GateType(gateType, pageable)
                .map(GateLogResponse::new);
    }

    // 특정 기간 내 통과 이력 조회
    public Page<GateLogResponse> getGateLogsBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        return gateLogRepository.findByPassAtBetween(start, end, pageable)
                .map(GateLogResponse::new);
    }

    // 게이트 이력 수정
    @Transactional
    public GateLogResponse updateGateLog(Long gateLogId, GateLogUpdateRequest request) {
        GateLog gateLog = gateLogRepository.findById(gateLogId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게이트 통과 이력 ID입니다: " + gateLogId));

        // gateId 또는 gateCode가 오면 게이트 재배정, 둘 다 없으면 null → 기존 게이트 유지
        Gate newGate = resolveGate(request.getGateId(), request.getGateCode());

        gateLog.update(request, newGate);

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