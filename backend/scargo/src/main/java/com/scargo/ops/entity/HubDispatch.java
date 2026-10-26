package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 hub_dispatches. 지선 출고 도크 연결 = dock_id (DB 설계 확정: shipment_legs.out_dock_id 대신) */
@Entity @Table(name = "hub_dispatches")
@Getter @Setter @NoArgsConstructor
public class HubDispatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "dispatch_id") private Long dispatchId;
    @Column(name = "assignment_id", nullable = false) private Long assignmentId;
    @Column(name = "hub_id", nullable = false) private Long hubId;
    @Column(name = "dock_id") private Long dockId;
    @Column(name = "region_id", nullable = false) private Long regionId;
    @Column(name = "delivery_vehicle_no", length = 20, nullable = false) private String deliveryVehicleNo;
    @Column(name = "parcel_count", nullable = false) private Integer parcelCount = 0;
    @Column(name = "total_weight_kg", nullable = false, precision = 8, scale = 1) private BigDecimal totalWeightKg = BigDecimal.ZERO;
    @Column(name = "departed_at") private OffsetDateTime departedAt;
}
