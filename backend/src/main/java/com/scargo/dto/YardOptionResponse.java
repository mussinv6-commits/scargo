package com.scargo.dto;

import com.scargo.entity.Yard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YardOptionResponse {

    private Long yardId;        // 야드 고유 ID
    private String yardName;    // 야드 이름 (예: A야드, 제1컨테이너)
    private String yardType;    // 야드 용도 (일반/냉동/위험물 등)

    // Entity -> DTO 변환 생성자
    public YardOptionResponse(Yard yard) {
        this.yardId = yard.getYardId();
        this.yardName = yard.getYardName();
        this.yardType = yard.getYardType();
    }

    // 정적 팩토리 메서드
    public static YardOptionResponse from(Yard yard) {
        return YardOptionResponse.builder()
                .yardId(yard.getYardId())
                .yardName(yard.getYardName())
                .yardType(yard.getYardType())
                .build();
    }
}