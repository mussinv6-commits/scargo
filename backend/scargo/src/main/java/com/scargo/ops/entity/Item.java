package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 items (화면에서 쓰는 컬럼만). destination_center_id = 박스 목적지 센터, status 'DAMAGED' = 파손 */
@Entity @Table(name = "items")
@Getter @Setter @NoArgsConstructor
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "item_id") private Long itemId;
    @Column(name = "tracking_no", length = 80, nullable = false, unique = true) private String trackingNo;
    @Column(name = "barcode", length = 100, nullable = false, unique = true) private String barcode;
    @Column(name = "shipment_id", nullable = false) private Long shipmentId;
    @Column(name = "container_no", length = 11) private String containerNo;
    @Column(name = "item_name", length = 150, nullable = false) private String itemName;
    @Column(name = "quantity", nullable = false) private Integer quantity = 1;
    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 3) private BigDecimal weightKg = BigDecimal.ZERO;
    @Column(name = "destination_center_id", nullable = false) private Long destinationCenterId;
    @Column(name = "status", length = 30, nullable = false) private String status = "IN_CONTAINER";
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
