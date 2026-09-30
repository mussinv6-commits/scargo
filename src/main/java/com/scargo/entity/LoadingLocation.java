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
    private Long locationId;              // 장소 고유 ID, 기본키

    @Column(name = "yard_id", nullable = false)
    private Long yardId;                  // 소속 야드 ID (yards 테이블 외래키, 필수)

    @Column(name = "sector", nullable = false, length = 50)
    private String sector;                // 섹터 또는 블록명 (필수)

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;          // 위도 좌표 (프론트엔드 위치 지정용)

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;         // 경도 좌표 (프론트엔드 위치 지정용)

    @Column(name = "status", length = 20)
    private String status;                // 섹터/위치 상태

    @Column(name = "is_available")
    private Boolean isAvailable;          // 단순 사용 가능 여부

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;     // 장소 정보 등록 일시
}