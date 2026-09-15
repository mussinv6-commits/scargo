package com.scargo.dto;

import com.scargo.entity.Truck;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
public class TruckResponse {
    private String vehicleNo;
    private Long companyId;
    private boolean semiTrailer;
    private String trailerNo;
    private String truckType;
    private BigDecimal maxLoadWeight;
    private String plannedRoute;
    private OffsetDateTime createdAt;

    public TruckResponse(Truck truck) {
        this.vehicleNo = truck.getVehicleNo();
        this.companyId = truck.getCompanyId();
        this.semiTrailer = truck.isSemiTrailer();
        this.trailerNo = truck.getTrailerNo();
        this.truckType = truck.getTruckType();
        this.maxLoadWeight = truck.getMaxLoadWeight();
        this.plannedRoute = truck.getPlannedRoute();
        this.createdAt = truck.getCreatedAt();
    }
}