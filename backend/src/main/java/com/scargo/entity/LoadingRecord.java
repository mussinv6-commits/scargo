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
    private Truck truck;                    // 적재한 화물차 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_no", nullable = false)
    private Container container;            // 적재된 컨테이너 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private LoadingLocation location;       // 적재가 이루어진 장소 (외래키)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoadingStatus status = LoadingStatus.IN_PROGRESS; // 작업 상태 (기본값: IN_PROGRESS)

    @Column(name = "loaded_at", insertable = false, updatable = false)
    private OffsetDateTime loadedAt;        // 적재 시각

    // 비즈니스 수정 메서드 
    public void update(Truck truck, Container container, LoadingLocation location, LoadingStatus status) {
        if (truck != null) {
            this.truck = truck;
        }
        if (container != null) {
            this.container = container;
        }
        if (location != null) {
            this.location = location;
        }
        if (status != null) {
            this.status = status;
        }
    }

    // 작업 상태 제어를 위한 별도 편의 메서드 (선택 사항)
    public void updateStatus(LoadingStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    // 작업 상태 Enum 정의
    public enum LoadingStatus {
        IN_PROGRESS,
        COMPLETED,
        CANCELED
    }
}