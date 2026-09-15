package com.scargo.dto;

import com.scargo.entity.Container;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor //기본생성자
public class ContainerResponse {
    private String containerNo;
    private Long companyId;
    private String reservedCargoInfo;
    private Long loadingLocationId;
    private String containerType;
    private OffsetDateTime createdAt;

    public ContainerResponse(Container container) {
        this.containerNo = container.getContainerNo();
        this.companyId = container.getCompanyId();
        this.reservedCargoInfo = container.getReservedCargoInfo();
        if (container.getLoadingLocation() != null) {
            this.loadingLocationId = container.getLoadingLocation().getLocationId();
        }
        this.containerType = container.getContainerType();
        this.createdAt = container.getCreatedAt();
    }
}