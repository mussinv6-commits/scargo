package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "containers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Container {

    @Id
    @Column(name = "container_no", length = 11, nullable = false)
    private String containerNo; // 컨테이너 고유번호 (ISO 6346)

    @Column(name = "company_id")
    private Long companyId; // 화물을 예약한 회사 ID (미매칭 시 NULL 허용)

    @Column(name = "iso_size_type_code", length = 4, nullable = false)
    private String isoSizeTypeCode; // 규격·종류 코드 (예: 45G1)

    @Column(name = "container_type", length = 20, nullable = false)
    private String containerType; // 화물 성격 분류 (일반/냉동 등)

    @Column(name = "is_high_cube", nullable = false)
    @Builder.Default
    private Boolean isHighCube = false; // 하이큐브(High Cube) 여부

    @Column(name = "max_gross_kg", precision = 8, scale = 1, nullable = false)
    private BigDecimal maxGrossKg; // 최대 총중량 MAX GROSS (kg)

    @Column(name = "tare_kg", precision = 8, scale = 1, nullable = false)
    private BigDecimal tareKg; // 컨테이너 자체 무게 TARE (kg)

    @Column(name = "net_kg", precision = 8, scale = 1, nullable = false)
    private BigDecimal netKg; // 최대 적재중량 NET = MAX GROSS - TARE (kg)

    @Column(name = "cubic_capacity_cbm", precision = 6, scale = 2, nullable = false)
    private BigDecimal cubicCapacityCbm; // 내부 용적 CU. CAP. (CBM)

    @Column(name = "csc_approval_no", length = 30)
    private String cscApprovalNo; // CSC 안전승인판 승인번호

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reserved_cargo_info", columnDefinition = "jsonb")
    private String reservedCargoInfo; // 화물 상세 정보 (JSONB 포맷)

    // [추가] 매핑된 차량 번호 및 배정 일시
    @Column(name = "assigned_vehicle_no", length = 20)
    private String assignedVehicleNo; // 배정된 차량 번호 (미배정 시 NULL)

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt; // 차량 배정 일시

    // 외래키 매핑 (loading_locations 테이블의 location_id 참조, 미지정 허용)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loading_location_id", nullable = true)
    private LoadingLocation loadingLocation;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 생성 일시

    // --- 도메인 로직 메서드 ---

    // 차량 배정 처리
    public void assignVehicle(String vehicleNo, OffsetDateTime assignedAt) {
        this.assignedVehicleNo = vehicleNo;
        this.assignedAt = assignedAt;
    }

    // 차량 배정 해제
    public void unassignVehicle() {
        this.assignedVehicleNo = null;
        this.assignedAt = null;
    }
}