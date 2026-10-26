package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 차량 제원 (trucks 와 1:1) - 출처: 번호판 제원 조회 xlsx */
@Entity
@Table(name = "vehicle_specs")
@Getter
@Setter
@NoArgsConstructor
public class VehicleSpec {
    @Id
    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Transient   // 26.10.20: 통합 DB vehicle_specs 에 없음 (차량 구분은 trucks.vehicle_category). TODO: 필요하면 trucks 조인으로
    private String vehicleCategory;

    @Column(name = "usage_purpose", length = 50)
    private String usagePurpose;

    @Column(name = "empty_weight_kg")
    private Integer emptyWeightKg;

    @Column(name = "gross_weight_kg")
    private Integer grossWeightKg;

    @Column(name = "max_payload_kg")
    private Integer maxPayloadKg;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "tire_count")
    private Integer tireCount;

    @Column(name = "axle_count")
    private Integer axleCount;

    @Column(name = "chassis_height_m", precision = 4, scale = 2)
    private BigDecimal chassisHeightM;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
