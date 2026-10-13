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
    private Long recordId; // 적재기록 고유 ID, 기본키

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_no", nullable = false)
    private Truck truck; // 적재한 화물차 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_no", nullable = false)
    private Container container; // 적재된 컨테이너 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private LoadingLocation location; // 적재가 이루어진 장소 (외래키)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private LoadingStatus status = LoadingStatus.PENDING; // 작업 상태 기본값은 첫 OCR 전 PENDING

    @Column(name = "loaded_at", insertable = false, updatable = false)
    private OffsetDateTime loadedAt; // 적재 시각

    // 적재 기록 정보를 수정하는 메서드
    public void update(
            Truck truck,
            Container container,
            LoadingLocation location,
            LoadingStatus status
    ) {

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

    // 작업 상태만 변경하는 메서드
    public void updateStatus(LoadingStatus status) {

        if (status != null) {
            this.status = status;
        }
    }

    // 상하차 작업 상태
    public enum LoadingStatus {
        PENDING,        // 첫 OCR 전 작업 대기
        IN_PROGRESS,    // 상하차 작업 진행 중
        COMPLETED,      // 상하차 작업 완료
        CANCELED        // 과적 3회 이상 등으로 작업 취소
    }
}