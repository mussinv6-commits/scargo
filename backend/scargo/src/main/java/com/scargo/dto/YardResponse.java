package com.scargo.dto;

import com.scargo.entity.Yard;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class YardResponse {

    private final Long yardId;
    private final String yardName;
    private final String yardType;
    private final Double latitude;    
    private final Double longitude;    
    private final String status;
    private final Boolean isAvailable;
    private final OffsetDateTime createdAt;

    public YardResponse(Yard yard) {
        this.yardId = yard.getYardId();
        this.yardName = yard.getYardName();
        this.yardType = yard.getYardType();
        this.latitude = yard.getLatitude();     // 값 매핑
        this.longitude = yard.getLongitude();   // 값 매핑
        this.status = yard.getStatus();
        this.isAvailable = yard.getIsAvailable();
        this.createdAt = yard.getCreatedAt();
    }
}