package com.scargo.dto;

import com.scargo.entity.OverloadCheck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverloadCheckResponse {

    private Long checkId;
    private String vehicleNo;
    private String containerNo;

    // 목적 및 기본 중량 스펙
    private String usagePurpose;
    private Integer emptyVehicleWeight;
    private Integer totalWeight;
    private Integer maxPayload;
    private Integer vgmWeight;

    // 차축 및 타이어 사양
    private Integer tireCount;
    private Integer vehicleAxleCount;
    private Integer axleCount;

    // 축별 중량 (1~8축, kg)
    private Integer axle1Weight;
    private Integer axle2Weight;
    private Integer axle3Weight;
    private Integer axle4Weight;
    private Integer axle5Weight;
    private Integer axle6Weight;
    private Integer axle7Weight;
    private Integer axle8Weight;

    // 축별 윤중 좌/우 (1~8축, kg)
    private Integer axle1WheelLeft;
    private Integer axle1WheelRight;
    private Integer axle2WheelLeft;
    private Integer axle2WheelRight;
    private Integer axle3WheelLeft;
    private Integer axle3WheelRight;
    private Integer axle4WheelLeft;
    private Integer axle4WheelRight;
    private Integer axle5WheelLeft;
    private Integer axle5WheelRight;
    private Integer axle6WheelLeft;
    private Integer axle6WheelRight;
    private Integer axle7WheelLeft;
    private Integer axle7WheelRight;
    private Integer axle8WheelLeft;
    private Integer axle8WheelRight;

    // 판정 정보
    private Boolean isViolation;
    private String violationReason;
    private Integer retryCount;
    private Boolean isPassed;
    private OffsetDateTime checkedAt;

    // Entity -> DTO 매핑 생성자
    public OverloadCheckResponse(OverloadCheck check) {
        this.checkId = check.getCheckId();
        this.vehicleNo = check.getVehicleNo();
        this.containerNo = check.getContainerNo();
        this.usagePurpose = check.getUsagePurpose();
        this.emptyVehicleWeight = check.getEmptyVehicleWeight();
        this.totalWeight = check.getTotalWeight();
        this.maxPayload = check.getMaxPayload();
        this.vgmWeight = check.getVgmWeight();
        this.tireCount = check.getTireCount();
        this.vehicleAxleCount = check.getVehicleAxleCount();
        this.axleCount = check.getAxleCount();

        this.axle1Weight = check.getAxle1Weight();
        this.axle2Weight = check.getAxle2Weight();
        this.axle3Weight = check.getAxle3Weight();
        this.axle4Weight = check.getAxle4Weight();
        this.axle5Weight = check.getAxle5Weight();
        this.axle6Weight = check.getAxle6Weight();
        this.axle7Weight = check.getAxle7Weight();
        this.axle8Weight = check.getAxle8Weight();

        this.axle1WheelLeft = check.getAxle1WheelLeft();
        this.axle1WheelRight = check.getAxle1WheelRight();
        this.axle2WheelLeft = check.getAxle2WheelLeft();
        this.axle2WheelRight = check.getAxle2WheelRight();
        this.axle3WheelLeft = check.getAxle3WheelLeft();
        this.axle3WheelRight = check.getAxle3WheelRight();
        this.axle4WheelLeft = check.getAxle4WheelLeft();
        this.axle4WheelRight = check.getAxle4WheelRight();
        this.axle5WheelLeft = check.getAxle5WheelLeft();
        this.axle5WheelRight = check.getAxle5WheelRight();
        this.axle6WheelLeft = check.getAxle6WheelLeft();
        this.axle6WheelRight = check.getAxle6WheelRight();
        this.axle7WheelLeft = check.getAxle7WheelLeft();
        this.axle7WheelRight = check.getAxle7WheelRight();
        this.axle8WheelLeft = check.getAxle8WheelLeft();
        this.axle8WheelRight = check.getAxle8WheelRight();

        this.isViolation = check.getIsViolation();
        this.violationReason = check.getViolationReason();
        this.retryCount = check.getRetryCount();
        this.isPassed = check.getIsPassed();
        this.checkedAt = check.getCheckedAt();
    }
}