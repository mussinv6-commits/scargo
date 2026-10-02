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

    // 26.10.01 병합: 게이트 지정 방식 2가지 모두 지원 (둘 중 하나는 필수, 둘 다 오면 gateId 우선)
    // - gateId   : 게이트 마스터 숫자 ID (태수님 방식, 프론트 화면용)
    // - gateCode : 사람이 읽기 쉬운 게이트 코드 예) 'Gate-ABC-01' (길웅님 방식, OCR/파이썬 장비 연동용)
    //   검증은 GateLogService.resolveGate() 에서 처리
    private Long gateId; // 게이트 마스터 고유 ID (외래 키 매핑용)

    @Size(max = 50, message = "게이트 코드는 최대 50자까지 입력 가능합니다.")
    private String gateCode; // 게이트 마스터 코드 (gates.gate_code)

    @Size(max = 20, message = "전면 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedPlateNo; // 전면 번호판 OCR 결과

    @Size(max = 20, message = "트레일러 번호판은 최대 20자까지 입력 가능합니다.")
    private String recognizedTrailerNo; // 후면/트레일러 번호판 OCR 결과

    @Size(max = 20, message = "매칭 차량 번호는 최대 20자까지 입력 가능합니다.")
    private String actualVehicleNo; // 매칭된 차량 번호판 (trucks FK)

    // 26.10.02 추가: OCR 검사 구분 (ENTRY: 입차 OCR, EXIT: 출차 OCR)
    @Pattern(
            regexp = "^(ENTRY|EXIT)$",
            message = "OCR 구분은 'ENTRY' 또는 'EXIT'만 허용됩니다."
    )
    private String scanType;

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