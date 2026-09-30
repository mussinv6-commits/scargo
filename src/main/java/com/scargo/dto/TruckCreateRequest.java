package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class TruckCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 항목입니다.")
    private String vehicleNo;

    @NotNull(message = "소속 업체 ID는 필수 입력 항목입니다.")
    private Long companyId;

    private boolean isSemiTrailer;

    private String trailerNo;

    private String truckType;

    @Positive(message = "최대 적재 중량은 양수여야 합니다.")
    private BigDecimal maxLoadWeight;

    private String plannedRoute; // JSON 포맷 문자열
}