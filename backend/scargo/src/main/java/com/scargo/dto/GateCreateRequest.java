package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class GateCreateRequest {

    @NotBlank(message = "게이트 코드는 필수 입력 값입니다.")
    @Size(max = 50, message = "게이트 코드는 최대 50자까지 입력 가능합니다.")
    private String gateCode; // 게이트 관리용 코드 (예: 'GATE_IN_01')

    @NotBlank(message = "게이트 이름은 필수 입력 값입니다.")
    @Size(max = 100, message = "게이트 이름은 최대 100자까지 입력 가능합니다.")
    private String gateName; // 게이트 이름 (예: '제1 물류문(정문)')

    @NotBlank(message = "게이트 유형은 필수 입력 값입니다.")
    @Pattern(regexp = "^(IN|OUT|BOTH)$", message = "게이트 유형은 'IN', 'OUT', 'BOTH' 중 하나여야 합니다.")
    private String gateType; // 게이트 유형 ('IN': 입구, 'OUT': 출구, 'BOTH': 출구+입구)

    private BigDecimal latitude; // 위도 (GPS 좌표)

    private BigDecimal longitude; // 경도 (GPS 좌표)

    private String locationDescription; // 상세 위치 설명 (예: 'A동 물류창고 우측 출입구')

    @NotNull(message = "사용 여부는 필수 값입니다.")
    private Boolean isActive = true; // 사용 여부 (기본값 True)
}