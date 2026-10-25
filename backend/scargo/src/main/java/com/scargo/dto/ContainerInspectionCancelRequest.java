package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 26.10.23 점검표 취소 (사유 필수)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInspectionCancelRequest {

    @NotBlank(message = "취소 사유를 입력해주세요.")
    @Size(max = 500, message = "취소 사유는 500자 이하입니다.")
    private String reason;
}
