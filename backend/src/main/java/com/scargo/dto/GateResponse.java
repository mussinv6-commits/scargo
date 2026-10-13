package com.scargo.dto;

import com.scargo.entity.Gate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateResponse {

    private Long gateId;                 // 게이트 고유 ID (PK)
    private String gateCode;             // 게이트 관리 코드 (예: 'GATE_IN_01')
    private String gateName;             // 게이트 이름 (예: '제1 물류문(정문)')
    private String gateType;             // 게이트 유형 ('IN', 'OUT', 'BOTH')
    private BigDecimal latitude;         // 위도
    private BigDecimal longitude;        // 경도
    private String locationDescription;// 상세 위치 설명
    private Boolean isActive;            // 사용 여부
    private OffsetDateTime createdAt;  // 생성 일시

    // Gate 엔티티를 GateResponse DTO로 변환하는 정적 팩토리 메서드
    public static GateResponse from(Gate gate) {
        if (gate == null) {
            return null;
        }
        return GateResponse.builder()
                .gateId(gate.getGateId())
                .gateCode(gate.getGateCode())
                .gateName(gate.getGateName())
                .gateType(gate.getGateType())
                .latitude(gate.getLatitude())
                .longitude(gate.getLongitude())
                .locationDescription(gate.getLocationDescription())
                .isActive(gate.getIsActive())
                .createdAt(gate.getCreatedAt())
                .build();
    }
}