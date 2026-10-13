package com.scargo.dto;

import com.scargo.entity.LoadingRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingRecordResponse {

    private Long recordId;
    private String vehicleNo;
    private String containerNo;
    private Long locationId;
    private String locationName; // 클라이언트 화면 편의용 장소명 (sector 값 매핑)
    private String status;       // 작업 상태 (IN_PROGRESS, COMPLETED, CANCELED 등)
    private OffsetDateTime loadedAt;

    public LoadingRecordResponse(LoadingRecord record) {
        if (record == null) {
            return;
        }

        this.recordId = record.getRecordId();
        
        if (record.getTruck() != null) {
            this.vehicleNo = record.getTruck().getVehicleNo();
        }
        
        if (record.getContainer() != null) {
            this.containerNo = record.getContainer().getContainerNo();
        }
        
        if (record.getLocation() != null) {
            this.locationId = record.getLocation().getLocationId();
            this.locationName = record.getLocation().getSector(); // LoadingLocation의 sector 필드 매핑
        }

        if (record.getStatus() != null) {
            this.status = record.getStatus().name();
        }
        
        this.loadedAt = record.getLoadedAt();
    }

    // 정적 팩토리 메서드 (Stream .map(LoadingRecordResponse::from) 지원)
    public static LoadingRecordResponse from(LoadingRecord record) {
        return new LoadingRecordResponse(record);
    }
}