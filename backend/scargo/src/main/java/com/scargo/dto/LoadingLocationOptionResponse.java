package com.scargo.dto;

import com.scargo.entity.LoadingLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingLocationOptionResponse {

    private Long locationId;    // 장소 고유 ID (기본키)
    private Long yardId;        // 소속 야드 ID
    private String sector;      // 섹터/블록명 (화면에 표시할 주요 식별 이름)
    private String status;      // 장소 상태
    private Boolean isAvailable; // 단순 사용 가능 여부

    // 엔티티 -> DTO 변환 정적 팩토리 메서드
    public static LoadingLocationOptionResponse from(LoadingLocation location) {
        if (location == null) {
            return null;
        }

        return LoadingLocationOptionResponse.builder()
                .locationId(location.getLocationId())
                .yardId(location.getYardId())
                .sector(location.getSector())
                .status(location.getStatus())
                .isAvailable(location.getIsAvailable())
                .build();
    }
}