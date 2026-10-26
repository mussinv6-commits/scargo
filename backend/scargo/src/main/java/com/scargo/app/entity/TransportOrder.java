package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 26.10.03 2차: 운송오더 - 기사 앱의 중심 테이블.
 * 차량번호 + 컨테이너번호로 조회하면 출발 터미널·목적지 HUB 가 자동으로 채워진다.
 * 연관 엔티티는 단순하게 ID(Long/String)로만 들고, 필요할 때 리포지토리로 조회한다.
 */
@Entity
@Table(name = "transport_orders")
@Getter
@Setter
@NoArgsConstructor
public class TransportOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", nullable = false, unique = true, length = 30)
    private String orderNo;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "container_no", nullable = false, length = 11)
    private String containerNo;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(name = "driver_account_id")
    private Long driverAccountId;

    @Column(name = "origin_terminal_id", nullable = false)
    private Long originTerminalId;

    @Column(name = "dest_hub_id", nullable = false)
    private Long destHubId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = OrderStatus.CREATED.name();

    @Column(name = "gate_out_log_id")
    private Long gateOutLogId;

    @Column(name = "overload_check_id")
    private Long overloadCheckId;

    @Column(name = "load_height_m", precision = 4, scale = 2)
    private BigDecimal loadHeightM;

    @Column(name = "vgm_kg")
    private Integer vgmKg;

    @Column(name = "gross_weight_kg")
    private Integer grossWeightKg;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "precheck", columnDefinition = "jsonb")
    private String precheck;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "planned_route", columnDefinition = "jsonb")
    private String plannedRoute;

    @Column(name = "planned_distance_km", precision = 7, scale = 2)
    private BigDecimal plannedDistanceKm;

    @Column(name = "eta")
    private OffsetDateTime eta;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "loaded_at")
    private OffsetDateTime loadedAt;

    @Column(name = "gate_out_at")
    private OffsetDateTime gateOutAt;

    @Column(name = "departed_at")
    private OffsetDateTime departedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "handed_over_at")
    private OffsetDateTime handedOverAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // 26.10.21 v10: 공컨 반납 (통합 SQL 12-2). RETURNING_EMPTY → RETURN_ARRIVED → EMPTY_RETURNED, HANDED_OVER 오더만
    // v12 통합 SQL 14-5 (사용자 확정): 손상은 반납을 막지 않음. damage_noted = 점검 DAMAGED 기록 (DB 트리거가 설정)
    @Column(name = "damage_noted", insertable = false, updatable = false)
    private Boolean damageNoted;
    @Column(name = "return_status", length = 20, insertable = false, updatable = false)
    private String returnStatus;
    @Column(name = "return_cy", columnDefinition = "text", insertable = false, updatable = false)
    private String returnCy;
    @Column(name = "return_cy_id", insertable = false, updatable = false)   // v11: return_cys (13_v11)
    private Long returnCyId;
    @Column(name = "return_receiver_name", length = 50, insertable = false, updatable = false)
    private String returnReceiverName;
    @Column(name = "return_eir_no", length = 30, insertable = false, updatable = false)
    private String returnEirNo;
    @Column(name = "return_ready_at", insertable = false, updatable = false)
    private OffsetDateTime returnReadyAt;
    @Column(name = "return_driver_account_id", insertable = false, updatable = false)
    private Long returnDriverAccountId;
    @Column(name = "return_instructed_at", insertable = false, updatable = false)
    private OffsetDateTime returnInstructedAt;
    @Column(name = "return_instructed_by_account_id", insertable = false, updatable = false)
    private Long returnInstructedByAccountId;
    @Column(name = "return_due_at", insertable = false, updatable = false)
    private OffsetDateTime returnDueAt;
    @Column(name = "return_started_at", insertable = false, updatable = false)
    private OffsetDateTime returnStartedAt;
    @Column(name = "return_arrived_at", insertable = false, updatable = false)
    private OffsetDateTime returnArrivedAt;
    @Column(name = "empty_returned_at", insertable = false, updatable = false)
    private OffsetDateTime emptyReturnedAt;
    @Column(name = "return_photo_url", columnDefinition = "text", insertable = false, updatable = false)
    private String returnPhotoUrl;
    @Column(name = "shipper_name", length = 100, insertable = false, updatable = false)
    private String shipperName;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PreUpdate
    @PrePersist
    void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean is(OrderStatus s) {
        return s.name().equals(this.status);
    }
}
