package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 item_vehicle_assignments: 물품 ↔ 운행. loaded_at / unloaded_at (지선 하차 시각은 여기) */
@Entity @Table(name = "item_vehicle_assignments", uniqueConstraints = @UniqueConstraint(columnNames = {"item_id", "assignment_id"}))
@Getter @Setter @NoArgsConstructor
public class ItemVehicleAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "item_assignment_id") private Long itemAssignmentId;
    @Column(name = "item_id", nullable = false) private Long itemId;
    @Column(name = "shipment_id", nullable = false) private Long shipmentId;
    @Column(name = "assignment_id", nullable = false) private Long assignmentId;
    @Column(name = "loaded_at") private OffsetDateTime loadedAt;
    @Column(name = "unloaded_at") private OffsetDateTime unloadedAt;
    @Column(name = "status", length = 20, nullable = false) private String status = "ASSIGNED"; // ASSIGNED | LOADED | UNLOADED | CANCELED
}
