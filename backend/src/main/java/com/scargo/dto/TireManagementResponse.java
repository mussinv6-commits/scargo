package com.scargo.dto;

import com.scargo.entity.TireManagement;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TireManagementResponse {

    private Long tireId;                    // 데이터 순번 (PK)
    private String vehicleNo;               // 차량 번호 (trucks FK)
    private String axlePosition;            // 차량 축 기준 위치 (예: 1축_좌, 1축_우 등)
    private String status;                  // 상태 (ACTIVE, REPLACED, DISCARDED)
    
    private OffsetDateTime installationDate; // 장착 일자
    private Integer installationMileage;    // 장착 시점의 차량 총 주행거리 (km)
    
    private OffsetDateTime disposalDate;    // 교체 또는 폐기된 일자
    private Integer disposalMileage;        // 교체 또는 폐기 시점의 차량 총 주행거리 (km)
    
    private String memo;                    // 특이사항 및 참고사항
    private OffsetDateTime createdAt;       // 생성일시
    private OffsetDateTime updatedAt;       // 수정일시

    // TireManagement 엔티티를 TireManagementResponse DTO로 변환
    public static TireManagementResponse from(TireManagement tire) {
        if (tire == null) {
            return null;
        }
        return TireManagementResponse.builder()
                .tireId(tire.getTireId())
                .vehicleNo(tire.getVehicleNo())
                .axlePosition(tire.getAxlePosition())
                .status(tire.getStatus())
                .installationDate(tire.getInstallationDate())
                .installationMileage(tire.getInstallationMileage())
                .disposalDate(tire.getDisposalDate())
                .disposalMileage(tire.getDisposalMileage())
                .memo(tire.getMemo())
                .createdAt(tire.getCreatedAt())
                .updatedAt(tire.getUpdatedAt())
                .build();
    }
}