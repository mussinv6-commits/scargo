package com.scargo.dto;

import com.scargo.entity.Container;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerResponse {

    private String containerNo;
    private Long companyId;
    private String isoSizeTypeCode;
    private String containerType;
    private Boolean isHighCube;
    private BigDecimal maxGrossKg;
    private BigDecimal tareKg;
    private BigDecimal netKg;
    private BigDecimal cubicCapacityCbm;
    private String cscApprovalNo;
    private String reservedCargoInfo;
    private Long loadingLocationId;
    private OffsetDateTime createdAt;

    // 26.10.01 병합: 사업자 매핑 화면(현재 매핑 목록)에서 배정 차량 표시용
    private String vehicleNo;          // 배정된 차량 번호 (containers.assigned_vehicle_no, 미배정이면 null)
    private OffsetDateTime assignedAt; // 차량 배정 일시

    public ContainerResponse(Container container) {
        this.containerNo = container.getContainerNo();
        this.companyId = container.getCompanyId();
        this.isoSizeTypeCode = container.getIsoSizeTypeCode();
        this.containerType = container.getContainerType();
        this.isHighCube = container.getIsHighCube();
        this.maxGrossKg = container.getMaxGrossKg();
        this.tareKg = container.getTareKg();
        this.netKg = container.getNetKg();
        this.cubicCapacityCbm = container.getCubicCapacityCbm();
        this.cscApprovalNo = container.getCscApprovalNo();
        this.reservedCargoInfo = container.getReservedCargoInfo();
        
        if (container.getLoadingLocation() != null) {
            this.loadingLocationId = container.getLoadingLocation().getLocationId();
        }
        
        this.createdAt = container.getCreatedAt();
        this.vehicleNo = container.getAssignedVehicleNo();
        this.assignedAt = container.getAssignedAt();
    }
}