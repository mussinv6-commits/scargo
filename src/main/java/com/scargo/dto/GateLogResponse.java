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

    // 2026-09-30 변경: gate_logs가 gate_id(FK)로 게이트 마스터를 참조하도록
    // 바뀌었지만, 프론트(Vue GateDemo.vue)가 이미 log.gateName / log.gateType을
    // 그대로 쓰고 있어서(테이블 렌더링) 프론트 수정 없이 호환되도록 Gate
    // 엔티티에서 이름/구분을 그대로 꺼내 평탄화해서 내려줌. gateId/gateCode는
    // 참고용으로 추가.
    private Integer gateId;
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

    public GateLogResponse(GateLog gateLog) {
        this.gateLogId = gateLog.getGateLogId();
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
}
