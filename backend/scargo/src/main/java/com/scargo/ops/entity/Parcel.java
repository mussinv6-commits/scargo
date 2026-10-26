package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 parcels = 박스 (item_id UNIQUE → items 1:1). 2차: unit_id(FK branch_units) 추가 */
@Entity @Table(name = "parcels", indexes = @Index(name = "idx_parcels_unit", columnList = "unit_id"))
@Getter @Setter @NoArgsConstructor
public class Parcel {
    @Id @Column(name = "parcel_no", length = 20) private String parcelNo;
    @Column(name = "item_id", nullable = false, unique = true) private Long itemId;
    @Column(name = "hub_id", nullable = false) private Long hubId;
    @Column(name = "status", length = 20, nullable = false) private String status = "IN_TRUCK"; // ... SORT_FAILED
    @Column(name = "handling", length = 20, nullable = false) private String handling = "일반";
    @Column(name = "dispatch_id") private Long dispatchId;
    @Column(name = "unit_id", length = 30) private String unitId;
}
