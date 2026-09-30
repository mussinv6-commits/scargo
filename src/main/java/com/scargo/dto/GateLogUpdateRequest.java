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
public class GateLogUpdateRequest {

    // 2026-09-30 변경: gateName/gateType 대신 게이트 마스터 코드로 재배정
    // (미입력 시 기존 게이트 유지 - GateLogService.updateGateLog() 참고)
    @Size(max = 50, message = "게이트 코드는 최대 50자까지 입력 가능합니다.")
    private String gateCode;

    @Size(max = 20, message = "전면 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedPlateNo; // 전면 번호판 OCR 결과

    @Size(max = 20, message = "트레일러 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedTrailerNo; // 후면/트레일러 번호판 OCR 결과

    @Size(max = 20, message = "매칭 차량 번호는 최대 20자까지 입력 가능합니다.")
    private String actualVehicleNo; // 수동으로 매칭/수정한 차량 번호판 (trucks FK)

    @DecimalMin(value = "0.00", message = "신뢰도는 0 이상이어야 합니다.")
    @DecimalMax(value = "100.00", message = "신뢰도는 100 이하여야 합니다.")
    @Digits(integer = 3, fraction = 2, message = "신뢰도는 소수점 둘째 자리까지 입력 가능합니다.")
    private BigDecimal plateConfidence; // OCR 신뢰도 (0.00 ~ 100.00%)

    @Pattern(regexp = "^(SUCCESS|FAILED)$", message = "처리 상태는 'SUCCESS' 또는 'FAILED'만 허용됩니다.")
    private String recognitionStatus; // 처리 상태 ('SUCCESS', 'FAILED')

    private String frontImageUrl; // 전면 이미지 URL

    private String rearImageUrl; // 후면 이미지 URL

    private String ocrRawData; // OCR 원본 (JSONB 포맷 문자열)

    @Size(max = 30, message = "차종은 최대 30자까지 입력 가능합니다.")
    private String vehicleType; // 차종
}
