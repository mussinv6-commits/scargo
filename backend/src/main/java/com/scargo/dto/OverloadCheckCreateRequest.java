package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class OverloadCheckCreateRequest {

    @NotBlank(message = "화물차 번호는 필수 입력 항목입니다.")
    @Size(max = 20, message = "화물차 번호는 최대 20자까지 입력 가능합니다.")
    private String vehicleNo; // 화물차 번호

    @Size(max = 20, message = "컨테이너 번호는 최대 20자까지 입력 가능합니다.")
    private String containerNo; // 컨테이너 번호 (선택)

    // 목적 및 기본 중량 스펙
    @Size(max = 50, message = "차량 용도는 최대 50자까지 입력 가능합니다.")
    private String usagePurpose; // 차량 용도/목적

    @PositiveOrZero(message = "공차중량은 0 이상이어야 합니다.")
    private Integer emptyVehicleWeight; // 공차중량 (kg)

    @PositiveOrZero(message = "총중량은 0 이상이어야 합니다.")
    private Integer totalWeight; // 총중량 (kg)

    @PositiveOrZero(message = "최대적재량은 0 이상이어야 합니다.")
    private Integer maxPayload; // 최대적재량 (kg)

    @PositiveOrZero(message = "VGM 총중량은 0 이상이어야 합니다.")
    private Integer vgmWeight; // VGM 총중량 (kg)

    // 차축 및 타이어 사양
    @PositiveOrZero(message = "타이어수는 0 이상이어야 합니다.")
    private Integer tireCount; // 타이어수

    @PositiveOrZero(message = "차축수는 0 이상이어야 합니다.")
    private Integer vehicleAxleCount; // 차축수

    @PositiveOrZero(message = "축수는 0 이상이어야 합니다.")
    private Integer axleCount; // 축수

    // 축별 중량 (1~8축, kg)
    @Builder.Default private Integer axle1Weight = 0;
    @Builder.Default private Integer axle2Weight = 0;
    @Builder.Default private Integer axle3Weight = 0;
    @Builder.Default private Integer axle4Weight = 0;
    @Builder.Default private Integer axle5Weight = 0;
    @Builder.Default private Integer axle6Weight = 0;
    @Builder.Default private Integer axle7Weight = 0;
    @Builder.Default private Integer axle8Weight = 0;

    // 축별 윤중 좌/우 (1~8축, kg)
    @Builder.Default private Integer axle1WheelLeft = 0;
    @Builder.Default private Integer axle1WheelRight = 0;
    @Builder.Default private Integer axle2WheelLeft = 0;
    @Builder.Default private Integer axle2WheelRight = 0;
    @Builder.Default private Integer axle3WheelLeft = 0;
    @Builder.Default private Integer axle3WheelRight = 0;
    @Builder.Default private Integer axle4WheelLeft = 0;
    @Builder.Default private Integer axle4WheelRight = 0;
    @Builder.Default private Integer axle5WheelLeft = 0;
    @Builder.Default private Integer axle5WheelRight = 0;
    @Builder.Default private Integer axle6WheelLeft = 0;
    @Builder.Default private Integer axle6WheelRight = 0;
    @Builder.Default private Integer axle7WheelLeft = 0;
    @Builder.Default private Integer axle7WheelRight = 0;
    @Builder.Default private Integer axle8WheelLeft = 0;
    @Builder.Default private Integer axle8WheelRight = 0;

    // 판정 정보
    @NotNull(message = "규정 위반 여부는 필수 입력 항목입니다.")
    private Boolean isViolation; // 규정 위반 여부

    private String violationReason; // 위반 사유

    @Builder.Default
    @PositiveOrZero(message = "재검증 시도 횟수는 0 이상이어야 합니다.")
    private Integer retryCount = 0; // 재검증 시도 횟수

    @NotNull(message = "최종 통과 여부는 필수 입력 항목입니다.")
    private Boolean isPassed; // 최종 통과 여부

    // 26.10.01 추가(계중대 정식화): 게이트 통과 기록 연결 / 계중대 코드 (선택)
    private Long gateLogId;

    @Size(max = 20, message = "계중대 코드는 최대 20자까지 입력 가능합니다.")
    private String stationCode;
}