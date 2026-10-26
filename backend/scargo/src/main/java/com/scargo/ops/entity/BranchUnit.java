package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 2차 branch_units (롤테이너). 박스 수는 parcels.unit_id 로 집계 (컬럼 없음) */
@Entity @Table(name = "branch_units")
@Getter @Setter @NoArgsConstructor
public class BranchUnit {
    @Id @Column(name = "unit_id", length = 30) private String unitId;
    @Column(name = "assignment_id") private Long assignmentId;                    // FK shipment_legs
    @Column(name = "dest_center_id", nullable = false) private Long destCenterId; // FK logistics_centers
    @Column(name = "status", length = 20, nullable = false) private String status = "SORTING"; // SORTING | LOADED | REJECTED | UNLOADED
    @Column(name = "loaded_at") private OffsetDateTime loadedAt;
    @Column(name = "unloaded_at") private OffsetDateTime unloadedAt;
}
