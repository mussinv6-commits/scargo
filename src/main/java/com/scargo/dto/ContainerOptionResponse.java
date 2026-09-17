package com.scargo.dto;

import com.scargo.entity.Container;
import lombok.Getter;

@Getter
public class ContainerOptionResponse {
    private String containerNo;
    private String containerType;
    private Long locationId;

    public ContainerOptionResponse(Container c) {
        this.containerNo = c.getContainerNo();
        this.containerType = c.getContainerType();
        this.locationId = c.getLoadingLocation() != null ? c.getLoadingLocation().getLocationId() : null;
    }
}