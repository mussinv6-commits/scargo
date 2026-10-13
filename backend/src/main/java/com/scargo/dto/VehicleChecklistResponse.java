package com.scargo.dto;

import com.scargo.entity.VehicleChecklist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleChecklistResponse {

    private Long inspectionId;
    private String vehicleNo;
    private Long inspectorAccountId;
    private LocalDate inspectionDate;

    // 점검 항목별 상태
    private String lightStatus;
    private String brakeStatus;
    private String airBrakeStatus;
    private String tireStatus;
    private String steeringStatus;
    private String cargoSecurementStatus;
    private String engineOilStatus;
    private String seatbeltStatus;
    private String fireExtinguisherStatus;
    private String safetyTriangleStatus;

    // 종합 판정 및 메모
    private String overallStatus;
    private String memo;

    // 등록/수정일시
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // Entity -> Response DTO 변환 메서드
    public static VehicleChecklistResponse from(VehicleChecklist checklist) {
        return VehicleChecklistResponse.builder()
                .inspectionId(checklist.getInspectionId())
                .vehicleNo(checklist.getVehicleNo())
                .inspectorAccountId(checklist.getInspectorAccountId())
                .inspectionDate(checklist.getInspectionDate())
                .lightStatus(checklist.getLightStatus())
                .brakeStatus(checklist.getBrakeStatus())
                .airBrakeStatus(checklist.getAirBrakeStatus())
                .tireStatus(checklist.getTireStatus())
                .steeringStatus(checklist.getSteeringStatus())
                .cargoSecurementStatus(checklist.getCargoSecurementStatus())
                .engineOilStatus(checklist.getEngineOilStatus())
                .seatbeltStatus(checklist.getSeatbeltStatus())
                .fireExtinguisherStatus(checklist.getFireExtinguisherStatus())
                .safetyTriangleStatus(checklist.getSafetyTriangleStatus())
                .overallStatus(checklist.getOverallStatus())
                .memo(checklist.getMemo())
                .createdAt(checklist.getCreatedAt())
                .updatedAt(checklist.getUpdatedAt())
                .build();
    }
}