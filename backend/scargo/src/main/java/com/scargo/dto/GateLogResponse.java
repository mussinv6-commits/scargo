package com.scargo.dto;

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
    // 26.10.01 병합(길웅님): 참고용 gateId/gateCode 추가 (gateName/gateType은 프론트 호환을 위해 그대로 유지)
    private Long gateId;
    private String gateCode;
    private String gateName;
    private String gateType;
    private String recognizedPlateNo;
    private String recognizedTrailerNo;
    private String actualVehicleNo;
    private BigDecimal plateConfidence;
    private String recognitionStatus;
    private String frontImageUrl;
    private String rearImageUrl;
    private String ocrRawData;
    private String vehicleType;
    private OffsetDateTime passAt;
    private OffsetDateTime createdAt;

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
    }

    // 서비스 계층에서 Stream API로 변환할 때 유용한 정적 팩토리 메서드
    public static GateLogResponse from(GateLog gateLog) {
        return new GateLogResponse(gateLog);
    }
}