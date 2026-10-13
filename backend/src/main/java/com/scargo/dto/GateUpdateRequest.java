package com.scargo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class GateUpdateRequest {

    @Size(max = 50, message = "게이트 코드는 최대 50자까지 입력 가능합니다.")
    private String gateCode; // 게이트 관리용 코드 (예: 'GATE_IN_01')

    @Size(max = 100, message = "게이트 이름은 최대 100자까지 입력 가능합니다.")
    private String gateName; // 게이트 이름 (예: '제1 물류문(정문)')

    @Pattern(regexp = "^(IN|OUT|BOTH)$", message = "게이트 유형은 'IN', 'OUT', 'BOTH' 중 하나여야 합니다.")
    private String gateType; // 게이트 유형 ('IN': 입문, 'OUT': 출문, 'BOTH': 양방향)

    private BigDecimal latitude; // 위도 (GPS 좌표)

    private BigDecimal longitude; // 경도 (GPS 좌표)

    private String locationDescription; // 상세 위치 설명

    private Boolean isActive; // 사용 여부 (활성/비활성)
}