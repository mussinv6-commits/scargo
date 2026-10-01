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
    }
}