package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleChecklistCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 값입니다.")
    private String vehicleNo;

    @NotNull(message = "점검자 계정 ID는 필수 입력 값입니다.")
    private Long inspectorAccountId;

    private LocalDate inspectionDate; // 입력하지 않으면 서버에서 오늘 날짜로 처리 가능

    // 각 점검 항목별 상태 (PASS, FAIL, NA) - 기본값 설정 가능
    @Builder.Default
    private String lightStatus = "PASS";

    @Builder.Default
    private String brakeStatus = "PASS";

    @Builder.Default
    private String airBrakeStatus = "PASS";

    @Builder.Default
    private String tireStatus = "PASS";

    @Builder.Default
    private String steeringStatus = "PASS";

    @Builder.Default
    private String cargoSecurementStatus = "PASS";

    @Builder.Default
    private String engineOilStatus = "PASS";

    @Builder.Default
    private String seatbeltStatus = "PASS";

    @Builder.Default
    private String fireExtinguisherStatus = "PASS";

    @Builder.Default
    private String safetyTriangleStatus = "PASS";

    @Builder.Default
    private String overallStatus = "PASS";

    private String memo;
}