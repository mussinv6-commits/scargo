package com.scargo.entity;

import com.scargo.dto.GateUpdateRequest;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "gates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gate_id")
    private Long gateId;

    @Column(name = "gate_code", nullable = false, unique = true, length = 50)
    private String gateCode;

    @Column(name = "gate_name", nullable = false, length = 100)
    private String gateName;

    @Column(name = "gate_type", nullable = false, length = 10)
    private String gateType; // 'IN', 'OUT', 또는 'BOTH' (입구,출구, 출입구)

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_description", columnDefinition = "TEXT")
    private String locationDescription;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // 게이트 정보 수정 메서드
    public void update(GateUpdateRequest request) {
        // 게이트 코드가 null이 아니면 수정
        if (request.getGateCode() != null) {
            this.gateCode = request.getGateCode();
        }
        // 게이트 이름이 null이 아니면 수정
        if (request.getGateName() != null) {
            this.gateName = request.getGateName();
        }
        // 게이트 유형이 null이 아니면 수정
        if (request.getGateType() != null) {
            this.gateType = request.getGateType();
        }
        // 위도가 null이 아니면 수정
        if (request.getLatitude() != null) {
            this.latitude = request.getLatitude();
        }
        // 경도가 null이 아니면 수정
        if (request.getLongitude() != null) {
            this.longitude = request.getLongitude();
        }
        // 상세 위치가 null이 아니면 수정
        if (request.getLocationDescription() != null) {
            this.locationDescription = request.getLocationDescription();
        }
        // 사용 여부가 null이 아니면 수정
        if (request.getIsActive() != null) {
            this.isActive = request.getIsActive();
        }
    }

    // 게이트 소프트 삭제 메서드 (사용 여부를 false로 변경)
    public void delete() {
        this.isActive = false;
    }
}