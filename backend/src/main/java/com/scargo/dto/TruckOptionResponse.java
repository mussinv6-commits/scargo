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

    // 26.09.22 추가: 사업자 화면에서 기사 배정 여부를 보여주기 위해 추가
    private Long assignedAccountId;
    private String assignedDriverName;

    public TruckOptionResponse(Truck truck) {
        this.vehicleNo = truck.getVehicleNo();
        // Truck 엔티티의 getter가 isSemiTrailer()임을 명시
        this.isSemiTrailer = truck.isSemiTrailer();
        this.truckType = truck.getTruckType();

        // 26.09.22 추가: 연관관계 assignedDriver 객체 안전 처리
        if (truck.getAssignedDriver() != null) {
            this.assignedAccountId = truck.getAssignedDriver().getAccountId();
            this.assignedDriverName = truck.getAssignedDriver().getUserName();
        }
    }

    public static TruckOptionResponse from(Truck truck) {
        return new TruckOptionResponse(truck);
    }
}