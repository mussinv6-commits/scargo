package com.scargo.dto;

import com.scargo.entity.Truck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TruckResponse {

    private String vehicleNo;
    private Long companyId;
    private String companyName;        
    private boolean isSemiTrailer;
    private String trailerNo;
    private String truckType;
    private BigDecimal maxLoadWeight;
    private String plannedRoute;
    private String status;              
    private OffsetDateTime createdAt;
    private Long assignedAccountId;    // 26.09.22 추가: 전담 기사 계정 ID (없으면 null)
    private String assignedDriverName; // 26.09.22 추가: 전담 기사 이름 (없으면 null)
    private String entryApproval;      // 26.09.22 추가: 진입 허가 상태 (PENDING/APPROVED/REJECTED)

    // Entity -> DTO 변환 생성자
    public TruckResponse(Truck truck) {
        this.vehicleNo = truck.getVehicleNo();
        
        // 연관관계 Company 객체 안전 처리
        if (truck.getCompany() != null) {
            this.companyId = truck.getCompany().getCompanyId();
            this.companyName = truck.getCompany().getCompanyName();
        }
        
        this.isSemiTrailer = truck.isSemiTrailer();
        this.trailerNo = truck.getTrailerNo();
        this.truckType = truck.getTruckType();
        this.maxLoadWeight = truck.getMaxLoadWeight();
        this.plannedRoute = truck.getPlannedRoute();
        this.status = truck.getStatus();
        this.createdAt = truck.getCreatedAt();
        this.entryApproval = truck.getEntryApproval();

        // 26.09.22 추가: 연관관계 assignedDriver 객체 안전 처리
        if (truck.getAssignedDriver() != null) {
            this.assignedAccountId = truck.getAssignedDriver().getAccountId();
            this.assignedDriverName = truck.getAssignedDriver().getUserName();
        }
    }

    // 정적 팩토리 메서드 (Stream 및 서비스 레이어용)
    public static TruckResponse from(Truck truck) {
        return new TruckResponse(truck);
    }
}