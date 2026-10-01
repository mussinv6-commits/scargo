package com.scargo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 26.10.01 추가(계중대 정식화): 계량 저장 결과.
 * record = DB(overload_checks)에 저장된 기록, violations = 어떤 항목이 기준을 넘었는지.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WeighingResultResponse {

    private OverloadCheckResponse record;
    private List<Violation> violations;
    private int axleLimitKg;
    private int grossLimitKg;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Violation {
        private String type;      // AXLE(축하중) | GROSS(총중량)
        private Integer axleNo;   // AXLE일 때 몇 번째 축인지 (1부터), GROSS면 null
        private int measuredKg;
        private int limitKg;
    }
}
