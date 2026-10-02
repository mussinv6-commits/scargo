package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 26.10.01 병합: 매핑 수정 요청 (PUT /api/mappings/{containerNo}) - 컨테이너에 배정된 차량 변경
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MappingUpdateRequest {

    @NotBlank(message = "변경할 차량 번호는 필수 입력 항목입니다.")
    private String vehicleNo;
}
