package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 shipment_legs. 지선 = leg_type 'HUB_TO_CENTER' (shipment_id NULL).
 *  status 는 11차 CHECK 그대로 (ASSIGNED / IN_TRANSIT / ARRIVED / RECEIVED(fix_1440) / CANCELED).
 *  화면 단계(AT_HUB·LOADING·LOADED·UNLOADED·INSPECTED·RECEIVED)는 저장하지 않고 BranchService.phase() 로 계산 */
@Entity @Table(name = "shipment_legs")
@Getter @Setter @NoArgsConstructor
public class ShipmentLeg {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "assignment_id") private Long assignmentId;
    @Column(name = "shipment_id") private Long shipmentId;
    @Column(name = "vehicle_no", length = 20, nullable = false) private String vehicleNo;
    @Column(name = "from_center_id", nullable = false) private Long fromCenterId;   // 허브의 logistics_centers.center_id
    @Column(name = "to_center_id", nullable = false) private Long toCenterId;       // 목적지 센터
    @Column(name = "leg_sequence", nullable = false) private Integer legSequence = 1;
    @Column(name = "leg_type", length = 30, nullable = false) private String legType;
    @Column(name = "status", length = 20, nullable = false) private String status = "ASSIGNED";
    @Column(name = "departed_at") private OffsetDateTime departedAt;
    @Column(name = "arrived_at") private OffsetDateTime arrivedAt;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    // ---- 26.10.20 DB 설계 확정 추가 컬럼 (HUB_TO_CENTER 전용, 그 외 leg_type 은 NULL: CHECK) ----
    @Column(name = "driver_account_id") private Long driverAccountId;               // FK accounts
    @Column(name = "domestic_seal_no", length = 30) private String domesticSealNo;  // 국내 운송 봉인
    // fix_1300: 센터 도착 확인 봉인 (허브 출발 입력 domestic_seal_no 와 비교, 다르면 SEAL_MISMATCH 예외)
    @Column(name = "arrival_seal_no", length = 30) private String arrivalSealNo;
    @Column(name = "arrival_seal_checked_at") private OffsetDateTime arrivalSealCheckedAt;
    @Column(name = "arrival_seal_checked_by", length = 100) private String arrivalSealCheckedBy;
    @Column(name = "planned_units") private Integer plannedUnits;                   // >= 0
    @Column(name = "planned_depart_at") private OffsetDateTime plannedDepartAt;
    @Column(name = "inspected_at") private OffsetDateTime inspectedAt;
    @Column(name = "received_at") private OffsetDateTime receivedAt;
    // 26.10.20 최종: 허브 도착 시각 (CHECK hub_arrived_at IS NULL OR leg_type='HUB_TO_CENTER'). AT_HUB = hub_arrived_at 있음 AND departed_at 없음
    @Column(name = "hub_arrived_at") private OffsetDateTime hubArrivedAt;
    // 하차 시각 = item_vehicle_assignments.unloaded_at, 파손 수 = items.status='DAMAGED' 집계, 출고 도크 = hub_dispatches.dock_id
}
