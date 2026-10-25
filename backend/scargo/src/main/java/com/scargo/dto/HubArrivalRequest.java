package com.scargo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 26.10.23 허브 도착 확정 요청. 허브는 오더의 도착 허브로 정해지므로 받지 않는다
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HubArrivalRequest {

    @NotNull(message = "오더를 지정해주세요.")
    private Long orderId;

    // 근거가 되는 허브 입차 OCR 기록 (hub_vehicle_logs.log_id)
    @NotNull(message = "입차 OCR 기록을 지정해주세요.")
    private Long hubVehicleLogId;

    // 선택: 입고 도크 (그 허브의 IN 도크만)
    private Long dockId;
}
