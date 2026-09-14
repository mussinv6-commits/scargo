package com.scargo.dto;

import com.scargo.entity.LoadingLocation;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
public class LoadingLocationResponse {

    private Long locationId;
    private Long yardId;
    private String sector;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;
    private Boolean isAvailable;
    private OffsetDateTime createdAt;

    public LoadingLocationResponse(LoadingLocation location) {
        this.locationId = location.getLocationId();
        this.yardId = location.getYardId();
        this.sector = location.getSector();
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
        this.status = location.getStatus();
        this.isAvailable = location.getIsAvailable();
        this.createdAt = location.getCreatedAt();
    }
}