package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// 2026-09-30 추가: 게이트 마스터 테이블(gates) - 기존에는 gate_logs에 게이트명/구분을
// 문자열로 직접 저장했으나, schema.sql이 게이트 마스터 테이블(위경도/설명 등 포함)을
// 두고 gate_logs.gate_id로 참조하는 구조로 바뀜에 따라 신규 생성한 엔티티.
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
    @Column(name = "gate_id", nullable = false)
    private Integer gateId; // 게이트 마스터 ID (SERIAL)

    @Column(name = "gate_code", length = 50, nullable = false, unique = true)
    private String gateCode; // 게이트 관리용 코드 (예: 'Gate-ABC-01')

    @Column(name = "gate_name", length = 100, nullable = false)
    private String gateName; // 게이트 이름 (예: '제1 물류문(정문)')

    @Column(name = "gate_type", length = 10, nullable = false)
    private String gateType; // 게이트 유형 ('IN', 'OUT', 'BOTH')

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_description", columnDefinition = "TEXT")
    private String locationDescription;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
