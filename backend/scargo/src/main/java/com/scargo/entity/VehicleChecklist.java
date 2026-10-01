package com.scargo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicle_checklist")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Column(name = "inspector_account_id")
    private Long inspectorAccountId;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    // 점검 항목별 상태 (PASS, FAIL, NA)
    @Column(name = "light_status", nullable = false)
    @Builder.Default
    private String lightStatus = "PASS";

    @Column(name = "brake_status", nullable = false)
    @Builder.Default
    private String brakeStatus = "PASS";

    @Column(name = "air_brake_status", nullable = false)
    @Builder.Default
    private String airBrakeStatus = "PASS";

    @Column(name = "tire_status", nullable = false)
    @Builder.Default
    private String tireStatus = "PASS";

    @Column(name = "steering_status", nullable = false)
    @Builder.Default
    private String steeringStatus = "PASS";

    @Column(name = "cargo_securement_status", nullable = false)
    @Builder.Default
    private String cargoSecurementStatus = "PASS";

    @Column(name = "engine_oil_status", nullable = false)
    @Builder.Default
    private String engineOilStatus = "PASS";

    @Column(name = "seatbelt_status", nullable = false)
    @Builder.Default
    private String seatbeltStatus = "PASS";

    @Column(name = "fire_extinguisher_status", nullable = false)
    @Builder.Default
    private String fireExtinguisherStatus = "PASS";

    @Column(name = "safety_triangle_status", nullable = false)
    @Builder.Default
    private String safetyTriangleStatus = "PASS";

    // 종합 판정 및 메모
    @Column(name = "overall_status", nullable = false)
    @Builder.Default
    private String overallStatus = "PASS";

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    // 타임스탬프
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.inspectionDate == null) {
            this.inspectionDate = LocalDate.now();
        }
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // 점검표 정보 수정 메서드
    public void update(String lightStatus, String brakeStatus, String airBrakeStatus, String tireStatus,
                       String steeringStatus, String cargoSecurementStatus, String engineOilStatus,
                       String seatbeltStatus, String fireExtinguisherStatus, String safetyTriangleStatus,
                       String overallStatus, String memo) {
        this.lightStatus = lightStatus;
        this.brakeStatus = brakeStatus;
        this.airBrakeStatus = airBrakeStatus;
        this.tireStatus = tireStatus;
        this.steeringStatus = steeringStatus;
        this.cargoSecurementStatus = cargoSecurementStatus;
        this.engineOilStatus = engineOilStatus;
        this.seatbeltStatus = seatbeltStatus;
        this.fireExtinguisherStatus = fireExtinguisherStatus;
        this.safetyTriangleStatus = safetyTriangleStatus;
        this.overallStatus = overallStatus;
        this.memo = memo;
    }
}