package com.scargo.dto;

import com.scargo.entity.Container;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerOptionResponse {

    private String containerNo;
    private String isoSizeTypeCode;
    private String containerType;
    private Boolean isHighCube;
    private Long locationId;

    public ContainerOptionResponse(Container c) {
        this.containerNo = c.getContainerNo();
        this.isoSizeTypeCode = c.getIsoSizeTypeCode();
        this.containerType = c.getContainerType();
        this.isHighCube = c.getIsHighCube();
        this.locationId = c.getLoadingLocation() != null ? c.getLoadingLocation().getLocationId() : null;
    }
}