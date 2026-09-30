package com.scargo.dto;

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
public class OverloadCheckUpdateRequest {

    @Size(max = 20, message = "화물차 번호는 최대 20자까지 입력 가능합니다.")
    private String vehicleNo; // 화물차 번호 (오기 정정용)

    @Size(max = 20, message = "컨테이너 번호는 최대 20자까지 입력 가능합니다.")
    private String containerNo; // 컨테이너 번호 (오기 정정용)

    @Size(max = 50, message = "차량 용도는 최대 50자까지 입력 가능합니다.")
    private String usagePurpose; // 차량 용도/목적

    private Boolean isViolation; // 규정 위반 여부

    private String violationReason; // 위반 사유

    @PositiveOrZero(message = "재검증 시도 횟수는 0 이상이어야 합니다.")
    private Integer retryCount; // 재검증 시도 횟수

    private Boolean isPassed; // 최종 통과 여부
}