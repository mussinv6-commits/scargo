package com.scargo.dto;

import jakarta.validation.constraints.Min;
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
public class VehicleDailyLogUpdateRequest {

    private LocalDate drivingDate;       // 수정할 운행 일자

    @Min(value = 0, message = "일일 주행 거리는 0 이상이어야 합니다.")
    private Integer dailyDistance;       // 수정할 일일 주행 거리 (km)

    private String memo;                 // 수정할 메모
}