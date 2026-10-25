package com.scargo.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

// 26.10.23 재점검: 확정된 점검의 다음 기록(DRAFT)을 새로 만든다. 원본은 그대로
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInspectionReinspectRequest {

    private Map<String, Boolean> items;

    @Size(max = 1000, message = "메모는 1000자 이하입니다.")
    private String memo;

    @Size(max = 500, message = "재점검 사유는 500자 이하입니다.")
    private String reason;
}
