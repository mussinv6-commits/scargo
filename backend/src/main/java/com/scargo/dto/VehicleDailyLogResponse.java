package com.scargo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDailyLogResponse {

    private Long logId;                  // 운행 기록 순번
    private String vehicleNo;            // 차량 번호
    private LocalDate drivingDate;       // 운행 일자
    private Integer dailyDistance;       // 일일 주행 거리 (km)
    private Integer accumulatedMileage;  // 누적 주행 거리 (km)
    private String memo;                 // 메모
    private OffsetDateTime createdAt;    // 기록 생성 일시
}