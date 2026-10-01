package com.scargo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class VehicleDailyLogCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 값입니다.")
    private String vehicleNo;

    @NotNull(message = "운행 일자는 필수 입력 값입니다.")
    private LocalDate drivingDate;

    @NotNull(message = "일일 주행 거리는 필수 입력 값입니다.")
    @Min(value = 0, message = "일일 주행 거리는 0 이상이어야 합니다.")
    private Integer dailyDistance;

    private String memo; // 메모는 선택사항
}