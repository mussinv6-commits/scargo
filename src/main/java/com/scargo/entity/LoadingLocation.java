package com.scargo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "loading_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;               // 장소 고유 ID, 기본키

    @Column(name = "location_name", nullable = false, length = 50)
    private String locationName;           // 구역명 (예: A야드, 3번 크레인)

    @Column(name = "zone", length = 50)
    private String zone;                   // 상위 구역/블록 구분

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;           // 위도

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;          // 경도

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;      // 생성 일시
}