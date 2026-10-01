package com.scargo.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TireManagementUpdateRequest {

    private String status;                  // 상태 (ACTIVE, REPLACED, DISCARDED)
    
    private OffsetDateTime disposalDate;    // 교체 또는 폐기된 일자
    
    @PositiveOrZero(message = "폐기/교체 시점 주행거리는 0 이상이어야 합니다.")
    private Integer disposalMileage;        // 교체 또는 폐기 시점의 차량 총 주행거리 (km)
    
    private String memo;                    // 특이사항 및 참고사항
}