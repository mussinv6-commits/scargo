package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String vehicleNo; // 차량인식번호(번호판), 기본키

    @Column(name = "is_semi_trailer", nullable = false)
    private boolean semiTrailer; // 세미트레일러 여부

    @Column(name = "trailer_no", length = 20)
    private String trailerNo; // 트레일러 번호판 (세미트레일러인 경우)

    @Column(name = "truck_type", length = 30)
    private String truckType; // 차종

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 등록 일시
}