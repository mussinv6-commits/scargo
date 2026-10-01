package com.scargo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 26.10.01 추가(계중대 정식화): 계중대에서 한 대를 계량한 결과.
 * 축중기(또는 계중대 콘솔)가 축별 중량만 보내면, 총중량·위반 여부·통과 여부는 서버가 판정한다.
 * (화면에서 보낸 판정값을 그대로 믿지 않기 위함)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeighingRequest {

    // 게이트 OCR 통과 기록에서 넘어온 차량이면 그 기록 ID (번호를 직접 입력한 차량이면 null)
    private Long gateLogId;

    @NotBlank(message = "차량번호는 필수입니다.")
    @Size(max = 20, message = "차량번호는 최대 20자입니다.")
    private String vehicleNo;

    @Size(max = 20, message = "컨테이너 번호는 최대 20자입니다.")
    private String containerNo;

    @Size(max = 20, message = "계중대 코드는 최대 20자입니다.")
    private String stationCode;

    @NotEmpty(message = "축별 중량이 없습니다.")
    @Size(min = 2, max = 8, message = "축 수는 2~8축이어야 합니다.")
    @Valid
    private List<Axle> axles;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Axle {
        @NotNull(message = "축 중량이 비어 있습니다.")
        @PositiveOrZero(message = "축 중량은 0 이상이어야 합니다.")
        @Max(value = 60000, message = "축 중량이 측정 범위(60,000kg)를 벗어났습니다.")
        private Integer weightKg;

        @PositiveOrZero(message = "윤중은 0 이상이어야 합니다.")
        private Integer leftKg;

        @PositiveOrZero(message = "윤중은 0 이상이어야 합니다.")
        private Integer rightKg;
    }
}
