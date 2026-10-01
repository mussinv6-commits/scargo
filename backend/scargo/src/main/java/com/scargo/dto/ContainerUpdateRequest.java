package com.scargo.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class ContainerUpdateRequest {

    private Long companyId; // 화물을 예약한 회사 ID (NULL 허용)

    @Size(max = 4, message = "ISO 규격·종류 코드는 최대 4자입니다.")
    private String isoSizeTypeCode; // 규격·종류 코드 (예: 45G1)

    @Size(max = 20, message = "컨테이너 타입은 최대 20자입니다.")
    private String containerType; // 화물 성격 분류 (일반/냉동 등)

    private Boolean isHighCube; // 하이큐브(High Cube) 여부

    @Digits(integer = 7, fraction = 1, message = "최대 총중량은 소수점 1자리까지 입력 가능합니다.")
    @Positive(message = "최대 총중량은 양수여야 합니다.")
    private BigDecimal maxGrossKg; // 최대 총중량 MAX GROSS (kg)

    @Digits(integer = 7, fraction = 1, message = "컨테이너 자체 무게는 소수점 1자리까지 입력 가능합니다.")
    @Positive(message = "컨테이너 자체 무게는 양수여야 합니다.")
    private BigDecimal tareKg; // 컨테이너 자체 무게 TARE (kg)

    @Digits(integer = 7, fraction = 1, message = "최대 적재중량은 소수점 1자리까지 입력 가능합니다.")
    @Positive(message = "최대 적재중량은 양수여야 합니다.")
    private BigDecimal netKg; // 최대 적재중량 NET (kg)

    @Digits(integer = 4, fraction = 2, message = "내부 용적은 소수점 2자리까지 입력 가능합니다.")
    @Positive(message = "내부 용적은 양수여야 합니다.")
    private BigDecimal cubicCapacityCbm; // 내부 용적 CU. CAP. (CBM)

    @Size(max = 30, message = "CSC 승인번호는 최대 30자입니다.")
    private String cscApprovalNo; // CSC 안전승인판 승인번호

    private String reservedCargoInfo; // 화물 상세 정보 (JSONB)

    private Long loadingLocationId; // 적재 장소 ID (NULL 허용)
}