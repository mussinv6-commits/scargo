package com.scargo.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerCreateRequest {

    @NotBlank(message = "컨테이너 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^[A-Z]{4}[0-9]{7}$", message = "컨테이너 번호 형식이 올바르지 않습니다. (예: BICU1234567)")
    @Size(max = 11)
    private String containerNo; // 소유자코드4 + 일련번호6 + 체크디지트1

    private Long companyId; // 화물을 예약한 회사 ID (NULL 허용)

    @NotBlank(message = "ISO 규격·종류 코드는 필수 입력 항목입니다.")
    @Size(max = 4)
    private String isoSizeTypeCode; // 규격·종류 코드 (예: 45G1)

    @NotBlank(message = "컨테이너 타입은 필수 입력 항목입니다.")
    @Size(max = 20)
    private String containerType; // 화물 성격 분류 (일반/냉동 등)

    @NotNull(message = "하이큐브 여부는 필수 입력 항목입니다.")
    private Boolean isHighCube; // 하이큐브(High Cube) 여부

    @NotNull(message = "최대 총중량은 필수 입력 항목입니다.")
    @Digits(integer = 7, fraction = 1)
    @Positive
    private BigDecimal maxGrossKg; // 최대 총중량 MAX GROSS (kg)

    @NotNull(message = "컨테이너 자체 무게는 필수 입력 항목입니다.")
    @Digits(integer = 7, fraction = 1)
    @Positive
    private BigDecimal tareKg; // 컨테이너 자체 무게 TARE (kg)

    @NotNull(message = "최대 적재중량은 필수 입력 항목입니다.")
    @Digits(integer = 7, fraction = 1)
    @Positive
    private BigDecimal netKg; // 최대 적재중량 NET (kg)

    @NotNull(message = "내부 용적은 필수 입력 항목입니다.")
    @Digits(integer = 4, fraction = 2)
    @Positive
    private BigDecimal cubicCapacityCbm; // 내부 용적 CU. CAP. (CBM)

    @Size(max = 30)
    private String cscApprovalNo; // CSC 안전승인판 승인번호

    private String reservedCargoInfo; // 화물 상세 정보 (JSONB 문자열)

    private Long loadingLocationId; // 적재 장소 ID (NULL 허용)
}