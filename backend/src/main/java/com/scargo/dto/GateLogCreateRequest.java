package com.scargo.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateLogCreateRequest {

    // 2026-09-30 변경: gate_logs가 게이트명/구분을 직접 저장하지 않고 게이트
    // 마스터 테이블(gates)을 gate_id로 참조하는 구조로 바뀜에 따라, 클라이언트는
    // DB가 자동 생성하는 숫자 gate_id 대신 사람이 읽기 쉬운 gate_code(예:
    // 'Gate-ABC-01')로 게이트를 지정함 - 서비스 단에서 gate_code -> Gate 조회.
    @NotBlank(message = "게이트 코드(gateCode)는 필수 입력 항목입니다.")
    @Size(max = 50, message = "게이트 코드는 최대 50자까지 입력 가능합니다.")
    private String gateCode; // 게이트 마스터 코드 (gates.gate_code)

    @Size(max = 20, message = "전면 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedPlateNo; // 전면 번호판 OCR 결과

    @Size(max = 20, message = "트레일러 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedTrailerNo; // 후면/트레일러 번호판 OCR 결과

    @Size(max = 20, message = "매칭 차량 번호는 최대 20자까지 입력 가능합니다.")
    private String actualVehicleNo; // 매칭된 차량 번호판 (trucks FK)

    @DecimalMin(value = "0.00", message = "신뢰도는 0 이상이어야 합니다.")
    @DecimalMax(value = "100.00", message = "신뢰도는 100 이하여야 합니다.")
    @Digits(integer = 3, fraction = 2, message = "신뢰도는 소수점 둘째 자리까지 입력 가능합니다.")
    private BigDecimal plateConfidence; // OCR 신뢰도 (0.00 ~ 100.00%)

    @Pattern(regexp = "^(SUCCESS|FAILED)$", message = "처리 상태는 'SUCCESS' 또는 'FAILED'만 허용됩니다.")
    @Builder.Default
    private String recognitionStatus = "SUCCESS"; // 처리 상태 ('SUCCESS', 'FAILED')

    private String frontImageUrl; // 전면 이미지 URL

    private String rearImageUrl; // 후면 이미지 URL

    private String ocrRawData; // OCR 원본 (JSONB 포맷 문자열)

    @Size(max = 30, message = "차종은 최대 30자까지 입력 가능합니다.")
    @Builder.Default
    private String vehicleType = "UNKNOWN"; // 차종

    private OffsetDateTime passAt; // 게이트 통과 일시 (미입력 시 서버 시각 적용)
}
