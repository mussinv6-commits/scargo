package com.scargo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "loading_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Long recordId;                 // 적재기록 고유 ID, 기본키

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_no", nullable = false)
    private Truck truck;                   // 적재한 화물차 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_no", nullable = false)
    private Container container;           // 적재된 컨테이너 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private LoadingLocation location;      // 적재가 이루어진 장소 (외래키)

    @Column(name = "loaded_at", insertable = false, updatable = false)
    private OffsetDateTime loadedAt;       // 적재 시각
}