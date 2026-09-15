package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class TruckCreateRequest {
    private String vehicleNo;
    private Long companyId;
    private boolean semiTrailer;
    private String trailerNo;
    private String truckType;
    private BigDecimal maxLoadWeight;
    private String plannedRoute;
}