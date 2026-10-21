package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.15 허브 입출차: hub_vehicles (허브 출입 등록 차량 · 차종은 DB가 정한다)
//  vehicle_kind: CARGO(입고 화물차) / COURIER(권역 출고 택배차)
@Entity
@Table(name = "hub_vehicles")
@Getter
@Setter
@NoArgsConstructor
public class HubVehicle {

    @Id
    @Column(name = "plate_no", length = 20)
    private String plateNo; // 공백 없이 저장 (예: 서울85자6013)

    @Column(name = "vehicle_kind", length = 10, nullable = false)
    private String vehicleKind;

    @Column(name = "carrier", length = 30)
    private String carrier;

    @Column(name = "memo", length = 100)
    private String memo;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public HubVehicle(String plateNo, String vehicleKind, String carrier, String memo) {
        this.plateNo = plateNo; this.vehicleKind = vehicleKind; this.carrier = carrier; this.memo = memo;
        this.createdAt = OffsetDateTime.now();
    }
}
