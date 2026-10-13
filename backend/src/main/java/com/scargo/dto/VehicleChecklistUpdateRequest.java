package com.scargo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleChecklistUpdateRequest {

    // 점검 항목별 상태 (PASS, FAIL, NA)
    private String lightStatus;
    private String brakeStatus;
    private String airBrakeStatus;
    private String tireStatus;
    private String steeringStatus;
    private String cargoSecurementStatus;
    private String engineOilStatus;
    private String seatbeltStatus;
    private String fireExtinguisherStatus;
    private String safetyTriangleStatus;

    // 종합 판정 및 메모
    private String overallStatus;
    private String memo;
}