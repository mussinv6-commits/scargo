package com.scargo.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

// 26.10.23 작성 중(DRAFT) 점검표 수정. items 는 보낸 값으로 통째로 바꾼다 (null 이면 그대로)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInspectionUpdateRequest {

    private Map<String, Boolean> items;

    @Size(max = 1000, message = "메모는 1000자 이하입니다.")
    private String memo;
}
