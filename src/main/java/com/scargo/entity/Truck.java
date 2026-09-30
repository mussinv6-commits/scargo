package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trucks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Truck {

    @Id
    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo; // 차량 번호판 (기본키)

    // Company 엔티티와의 연관관계 매핑 (FK: company_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // 소속 업체

    @Column(name = "is_semi_trailer", nullable = false)
    private boolean semiTrailer; // 세미트레일러 여부

    @Column(name = "trailer_no", length = 20)
    private String trailerNo; // 트레일러 번호판

    @Column(name = "truck_type", length = 30)
    private String truckType; // 차종

    @Column(name = "max_load_weight", precision = 8, scale = 2)
    private BigDecimal maxLoadWeight; // 최대 적재 허용 중량

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "planned_route", columnDefinition = "jsonb")
    private String plannedRoute; // 이동 경로 (JSON)

    //차량 상태 ('OUTSIDE', 'INSIDE', 'IN_TRANSIT')
    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "OUTSIDE"; 

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 차량 등록 일시

    // 비즈니스 메서드: 상태 업데이트
    public void updateStatus(String status) {
        this.status = status;
    }

    // 비즈니스 메서드: 전체 정보 업데이트
    public void update(Company company, Boolean semiTrailer, String trailerNo, String truckType, 
                       BigDecimal maxLoadWeight, String plannedRoute, String status) {
        if (company != null) this.company = company;
        if (semiTrailer != null) this.semiTrailer = semiTrailer;
        if (trailerNo != null) this.trailerNo = trailerNo;
        if (truckType != null) this.truckType = truckType;
        if (maxLoadWeight != null) this.maxLoadWeight = maxLoadWeight;
        if (plannedRoute != null) this.plannedRoute = plannedRoute;
        if (status != null) this.status = status;
    }
    
    public Long getCompanyId() {
        return this.company != null ? this.company.getCompanyId() : null; // Company의 PK 조회 메서드명에 맞게 조정 (getId 또는 getCompanyId)
    }
}