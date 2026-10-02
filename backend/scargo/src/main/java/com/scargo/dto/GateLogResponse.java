package com.scargo.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.entity.GateLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateLogResponse {

    private Long gateLogId;

    // 26.10.01 병합(길웅님): 참고용 gateId/gateCode 추가
    // gateName/gateType은 프론트 호환을 위해 그대로 유지
    private Long gateId;
    private String gateCode;
    private String gateName;
    private String gateType;

    private String recognizedPlateNo;
    private String recognizedTrailerNo;
    private String actualVehicleNo;

    // 26.10.02 추가: OCR 검사 구분 (ENTRY: 입차, EXIT: 출차)
    // DB 컬럼을 추가하지 않고 ocrRawData의 scanType 값을 응답용으로 추출
    private String scanType;

    private BigDecimal plateConfidence;
    private String recognitionStatus;
    private String frontImageUrl;
    private String rearImageUrl;
    private String ocrRawData;
    private String vehicleType;
    private OffsetDateTime passAt;
    private OffsetDateTime createdAt;

    // JSON 문자열 파싱용 ObjectMapper
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // GateLog 엔티티를 받아 Response DTO로 변환하는 생성자
    public GateLogResponse(GateLog gateLog) {

        this.gateLogId = gateLog.getGateLogId();

        // 연관된 Gate 엔티티가 존재할 경우에만 안전하게 가져오기
        if (gateLog.getGate() != null) {
            this.gateId = gateLog.getGate().getGateId();
            this.gateCode = gateLog.getGate().getGateCode();
            this.gateName = gateLog.getGate().getGateName();
            this.gateType = gateLog.getGate().getGateType();
        }

        this.recognizedPlateNo = gateLog.getRecognizedPlateNo();
        this.recognizedTrailerNo = gateLog.getRecognizedTrailerNo();
        this.actualVehicleNo = gateLog.getActualVehicleNo();
        this.plateConfidence = gateLog.getPlateConfidence();
        this.recognitionStatus = gateLog.getRecognitionStatus();
        this.frontImageUrl = gateLog.getFrontImageUrl();
        this.rearImageUrl = gateLog.getRearImageUrl();
        this.ocrRawData = gateLog.getOcrRawData();
        this.vehicleType = gateLog.getVehicleType();
        this.passAt = gateLog.getPassAt();
        this.createdAt = gateLog.getCreatedAt();

        // 26.10.02 추가: ocrRawData JSON에서 scanType(ENTRY/EXIT) 추출
        this.scanType = extractScanType(gateLog.getOcrRawData());
    }

    // ocrRawData JSON에서 scanType 추출
    private static String extractScanType(String ocrRawData) {

        if (ocrRawData == null || ocrRawData.isBlank()) {
            return null;
        }

        try {

            JsonNode root = OBJECT_MAPPER.readTree(ocrRawData);

            JsonNode scanTypeNode = root.get("scanType");

            if (scanTypeNode == null || scanTypeNode.isNull()) {
                return null;
            }

            String value = scanTypeNode.asText();

            if ("ENTRY".equalsIgnoreCase(value)) {
                return "ENTRY";
            }

            if ("EXIT".equalsIgnoreCase(value)) {
                return "EXIT";
            }

            return null;

        } catch (Exception e) {

            // 기존 OCR 기록 중 JSON 형식이 아닌 데이터가 있어도
            // 게이트 이력 조회 자체가 실패하지 않도록 null 처리
            return null;
        }
    }

    // 서비스 계층에서 Stream API로 변환할 때 유용한 정적 팩토리 메서드
    public static GateLogResponse from(GateLog gateLog) {
        return new GateLogResponse(gateLog);
    }
}