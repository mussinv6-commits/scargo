package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class YardCreateRequest {

    private String yardName;     // 야드명
    private String yardType;     // 야드 타입
    private String status;       // 야드 상태
    private Boolean isAvailable; // 야드 이용가능 여부
    private Double latitude;     // 위도
    private Double longitude;    // 경도

}