package com.scargo.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LoadingLocationUpdateRequest {

    private String sector;       // 섹터 또는 블록명
    private BigDecimal latitude; // 위도 좌표
    private BigDecimal longitude;// 경도 좌표
    private String status;       // 섹터/위치 상태
    private Boolean isAvailable; // 사용 가능 여부
}