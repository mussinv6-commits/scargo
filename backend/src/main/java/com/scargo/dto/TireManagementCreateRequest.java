package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TireManagementCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 값입니다.")
    private String vehicleNo;               // 차량 번호 (trucks FK)

    @NotBlank(message = "차량 축 위치는 필수 입력 값입니다.")
    private String axlePosition;            // 차량 축 기준 위치 (예: 1축_좌, 1축_우 등)

    private String status;                  // 상태 (기본값 'ACTIVE')

    private OffsetDateTime installationDate; // 장착 일자 (미입력 시 서버 현재 시간 처리)

    @NotNull(message = "장착 시점의 주행거리는 필수 입력 값입니다.")
    @PositiveOrZero(message = "장착 시점 주행거리는 0 이상이어야 합니다.")
    private Integer installationMileage;    // 장착 시점의 차량 총 주행거리 (km)

    private String memo;                    // 특이사항 및 참고사항
}