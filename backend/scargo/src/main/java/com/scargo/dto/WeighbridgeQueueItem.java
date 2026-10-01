package com.scargo.dto;

import com.scargo.entity.Container;
import com.scargo.entity.GateLog;
import com.scargo.entity.Truck;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 26.10.01 추가(계중대 정식화): 게이트 OCR을 통과해 계중대 계량을 기다리는 차량 1대.
 * gate_logs(게이트 통과 기록) + trucks(등록차량 정보)를 합쳐서 내려준다.
 */
@Getter
public class WeighbridgeQueueItem {

    private final Long gateLogId;
    private final String vehicleNo;          // 등록차량과 매칭된 번호 (actual_vehicle_no)
    private final String recognizedPlateNo;  // OCR이 읽은 그대로의 번호
    private final BigDecimal plateConfidence;
    private final String gateCode;
    private final String gateName;
    private final OffsetDateTime passAt;
    private final String frontImageUrl;

    // 등록차량 정보 (trucks) - 매칭된 차량이 삭제됐으면 null
    private final String truckType;
    private final String companyName;
    private final Boolean semiTrailer;
    private final String trailerNo;
    private final BigDecimal maxLoadWeight;
    private final int suggestedAxleCount; // 세미트레일러면 5축, 아니면 3축을 기본값으로 제안

    // 26.10.01 추가: 이 차량에 실린 컨테이너 (DB 자동 조회 - 차량 배정 매핑 → 최근 적재기록 순)
    private final String containerNo;
    private final String containerSizeType;   // ISO 규격·종류 코드 (예: 45G1)
    private final String containerType;       // 일반/냉동 등
    private final BigDecimal containerMaxGrossKg;
    private final BigDecimal containerTareKg;
    private final String containerSource;     // MAPPING(차량 배정) | LOADING_RECORD(적재기록) | null

    public WeighbridgeQueueItem(GateLog log, Truck truck) {
        this(log, log.getActualVehicleNo(), truck, null, null);
    }

    /** log 가 null 이면 게이트를 거치지 않은 차량(직접 계량/재계량)용 정보 */
    public WeighbridgeQueueItem(GateLog log, String vehicleNo, Truck truck, Container container, String containerSource) {
        this.gateLogId = log != null ? log.getGateLogId() : null;
        this.vehicleNo = vehicleNo;
        this.recognizedPlateNo = log != null ? log.getRecognizedPlateNo() : null;
        this.plateConfidence = log != null ? log.getPlateConfidence() : null;
        this.gateCode = log != null && log.getGate() != null ? log.getGate().getGateCode() : null;
        this.gateName = log != null && log.getGate() != null ? log.getGate().getGateName() : null;
        this.passAt = log == null ? null : (log.getPassAt() != null ? log.getPassAt() : log.getCreatedAt());
        this.frontImageUrl = log != null ? log.getFrontImageUrl() : null;

        if (container != null) {
            this.containerNo = container.getContainerNo();
            this.containerSizeType = container.getIsoSizeTypeCode();
            this.containerType = container.getContainerType();
            this.containerMaxGrossKg = container.getMaxGrossKg();
            this.containerTareKg = container.getTareKg();
            this.containerSource = containerSource;
        } else {
            this.containerNo = null;
            this.containerSizeType = null;
            this.containerType = null;
            this.containerMaxGrossKg = null;
            this.containerTareKg = null;
            this.containerSource = null;
        }

        if (truck != null) {
            this.truckType = truck.getTruckType();
            this.companyName = truck.getCompany() != null ? truck.getCompany().getCompanyName() : null;
            this.semiTrailer = truck.isSemiTrailer();
            this.trailerNo = truck.getTrailerNo();
            // 26.10.01: trucks.max_load_weight 는 톤 단위 → 화면에는 kg 로 내려줌
            Integer kg = com.scargo.service.WeighbridgeService.toKg(truck.getMaxLoadWeight());
            this.maxLoadWeight = kg != null ? BigDecimal.valueOf(kg) : null;
            this.suggestedAxleCount = truck.isSemiTrailer() ? 5 : 3;
        } else {
            this.truckType = null;
            this.companyName = null;
            this.semiTrailer = null;
            this.trailerNo = null;
            this.maxLoadWeight = null;
            this.suggestedAxleCount = 3;
        }
    }
}
