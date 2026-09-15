package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor  //기본생성자
public class ContainerUpdateRequest {
    private Long companyId;
    private String reservedCargoInfo;
    private Long loadingLocationId;
    private String containerType;
}