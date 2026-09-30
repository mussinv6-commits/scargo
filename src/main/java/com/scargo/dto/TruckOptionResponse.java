package com.scargo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scargo.entity.Truck;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TruckOptionResponse {

    private String vehicleNo;

    @JsonProperty("isSemiTrailer")
    private boolean isSemiTrailer;

    private String truckType;

    public TruckOptionResponse(Truck truck) {
        this.vehicleNo = truck.getVehicleNo();
        // Truck 엔티티의 getter가 isSemiTrailer()임을 명시
        this.isSemiTrailer = truck.isSemiTrailer();
        this.truckType = truck.getTruckType();
    }

    public static TruckOptionResponse from(Truck truck) {
        return new TruckOptionResponse(truck);
    }
}