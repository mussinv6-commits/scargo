package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MappingCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 항목입니다.")
    private String vehicleNo;

    @NotBlank(message = "컨테이너 번호는 필수 입력 항목입니다.")
    private String containerNo;
}