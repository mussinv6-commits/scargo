package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trucks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Truck {

    @Id
    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo; // 차량 번호판 (기본키)

    @Column(name = "company_id", nullable = false)
    private Long companyId; // 소속 업체 ID (companies 테이블 외래키)

    @Column(name = "is_semi_trailer", nullable = false)
    private boolean semiTrailer; // 세미트레일러 여부

    @Column(name = "trailer_no", length = 20)
    private String trailerNo; // 트레일러 번호판 (세미트레일러인 경우)

    @Column(name = "truck_type", length = 30)
    private String truckType; // 차종

    @Column(name = "max_load_weight", precision = 8, scale = 2)
    private BigDecimal maxLoadWeight; // 최대 적재 허용 가능 중량 (과적 기준치)

    @Column(name = "planned_route", columnDefinition = "jsonb")
    private String plannedRoute; // 이동 경로 (JSON 형식)

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 차량 등록 일시
}