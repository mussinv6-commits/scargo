package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "overload_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverloadCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id", nullable = false)
    private Long checkId; // 과적 검사 기록 ID (BIGSERIAL)

    @Column(name = "vehicle_no", length = 20, nullable = false)
    private String vehicleNo; // 화물차 번호 (trucks 테이블 외래키 연동)

    @Column(name = "container_no", length = 20)
    private String containerNo; // 컨테이너 번호 (containers 테이블 외래키 연동)

    // 목적 및 기본 중량 스펙
    @Column(name = "usage_purpose", length = 50)
    private String usagePurpose; // 차량 용도/목적

    @Column(name = "empty_vehicle_weight")
    private Integer emptyVehicleWeight; // 공차중량 (kg)

    @Column(name = "total_weight")
    private Integer totalWeight; // 총중량 (kg)

    @Column(name = "max_payload")
    private Integer maxPayload; // 최대적재량 (kg)

    @Column(name = "vgm_weight")
    private Integer vgmWeight; // VGM 총중량 (kg)

    // 차축 및 타이어 사양
    @Column(name = "tire_count")
    private Integer tireCount; // 타이어수

    @Column(name = "vehicle_axle_count")
    private Integer vehicleAxleCount; // 차축수

    @Column(name = "axle_count")
    private Integer axleCount; // 축수

    // 축별 중량 (1~8축, kg)
    @Builder.Default
    @Column(name = "axle1_weight") private Integer axle1Weight = 0;
    @Builder.Default
    @Column(name = "axle2_weight") private Integer axle2Weight = 0;
    @Builder.Default
    @Column(name = "axle3_weight") private Integer axle3Weight = 0;
    @Builder.Default
    @Column(name = "axle4_weight") private Integer axle4Weight = 0;
    @Builder.Default
    @Column(name = "axle5_weight") private Integer axle5Weight = 0;
    @Builder.Default
    @Column(name = "axle6_weight") private Integer axle6Weight = 0;
    @Builder.Default
    @Column(name = "axle7_weight") private Integer axle7Weight = 0;
    @Builder.Default
    @Column(name = "axle8_weight") private Integer axle8Weight = 0;

    // 축별 윤중 좌/우 (1~8축, kg)
    @Builder.Default
    @Column(name = "axle1_wheel_left") private Integer axle1WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle1_wheel_right") private Integer axle1WheelRight = 0;

    @Builder.Default
    @Column(name = "axle2_wheel_left") private Integer axle2WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle2_wheel_right") private Integer axle2WheelRight = 0;

    @Builder.Default
    @Column(name = "axle3_wheel_left") private Integer axle3WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle3_wheel_right") private Integer axle3WheelRight = 0;

    @Builder.Default
    @Column(name = "axle4_wheel_left") private Integer axle4WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle4_wheel_right") private Integer axle4WheelRight = 0;

    @Builder.Default
    @Column(name = "axle5_wheel_left") private Integer axle5WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle5_wheel_right") private Integer axle5WheelRight = 0;

    @Builder.Default
    @Column(name = "axle6_wheel_left") private Integer axle6WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle6_wheel_right") private Integer axle6WheelRight = 0;

    @Builder.Default
    @Column(name = "axle7_wheel_left") private Integer axle7WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle7_wheel_right") private Integer axle7WheelRight = 0;

    @Builder.Default
    @Column(name = "axle8_wheel_left") private Integer axle8WheelLeft = 0;
    @Builder.Default
    @Column(name = "axle8_wheel_right") private Integer axle8WheelRight = 0;

    // 판정 정보
    @Column(name = "is_violation", nullable = false)
    private Boolean isViolation; // 규정 위반 여부

    @Column(name = "violation_reason", columnDefinition = "TEXT")
    private String violationReason; // 위반 사유

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0; // 재검증 시도 횟수

    @Column(name = "is_passed", nullable = false)
    private Boolean isPassed; // 최종 통과 여부

    @Column(name = "checked_at", insertable = false, updatable = false)
    private OffsetDateTime checkedAt; // 검사 일시

    // 오기 정정용 도메인 메서드
    public void updateCorrection(String vehicleNo, String containerNo, String usagePurpose, 
                                 Boolean isViolation, String violationReason, 
                                 Integer retryCount, Boolean isPassed) {
        if (vehicleNo != null) this.vehicleNo = vehicleNo;
        if (containerNo != null) this.containerNo = containerNo;
        if (usagePurpose != null) this.usagePurpose = usagePurpose;
        if (isViolation != null) this.isViolation = isViolation;
        if (violationReason != null) this.violationReason = violationReason;
        if (retryCount != null) this.retryCount = retryCount;
        if (isPassed != null) this.isPassed = isPassed;
    }
}