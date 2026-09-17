package com.scargo.dto;

import com.scargo.entity.Truck;
import lombok.Getter;

@Getter
public class TruckOptionResponse {
    private String vehicleNo;
    private boolean semiTrailer;
    private String truckType;

    public TruckOptionResponse(Truck t) {
        this.vehicleNo = t.getVehicleNo();
        this.semiTrailer = t.isSemiTrailer();
        this.truckType = t.getTruckType();
    }
}