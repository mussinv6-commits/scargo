package com.scargo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tire_management")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TireManagement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tire_id")
    private Long tireId;                    // 데이터 순번 (PK)

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;               // 차량 번호 (trucks FK)

    // 26.10.21 DB 최종통합: 컬럼명 tire_position (axle_position 컬럼 없음). 자바 필드명은 기존 API 호환 위해 유지
    @Column(name = "tire_position", nullable = false, length = 50)
    private String axlePosition;            // 타이어 위치 (예: 1축_좌, 1축_우 등)

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";       // 상태 (ACTIVE, REPLACED, DISCARDED)

    // 장착 정보
    @Column(name = "installation_date")
    private OffsetDateTime installationDate; // 장착 일자

    @Column(name = "installation_mileage", nullable = false)
    private Integer installationMileage;    // 장착 시점의 차량 총 주행거리 (km)

    // 교체 / 폐기 정보
    @Column(name = "disposal_date")
    private OffsetDateTime disposalDate;    // 교체 또는 폐기된 일자

    @Column(name = "disposal_mileage")
    private Integer disposalMileage;        // 교체 또는 폐기 시점의 차량 총 주행거리 (km)

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;                    // 특이사항 및 참고사항

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;       // 생성일시

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;       // 수정일시

    // 엔티티 저장 전 자동 실행 (날짜 기본값 세팅)
    @PrePersist
    public void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.installationDate == null) {
            this.installationDate = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    // 엔티티 수정 전 자동 실행
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}