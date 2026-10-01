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
public class TruckUpdateRequest {

    private Long companyId; // 소속 업체 ID (Company 엔티티 FK)

    private Boolean semiTrailer; // 세미트레일러 여부

    @Size(max = 20, message = "트레일러 번호판은 최대 20자까지 입력 가능합니다.")
    private String trailerNo; // 트레일러 번호판

    @Size(max = 30, message = "차종은 최대 30자까지 입력 가능합니다.")
    private String truckType; // 차종

    @Digits(integer = 6, fraction = 2, message = "최대 적재 허용 중량은 소수점 2자리까지 입력 가능합니다.")
    @Positive(message = "최대 적재 허용 중량은 양수여야 합니다.")
    private BigDecimal maxLoadWeight; // 최대 적재 허용 중량

    private String plannedRoute; // 이동 경로 (JSONB 문자열)

    @Size(max = 20, message = "상태값은 최대 20자까지 입력 가능합니다.")
    private String status; // 차량 상태 ('OUTSIDE', 'INSIDE', 'IN_TRANSIT')
}