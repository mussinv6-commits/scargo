package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "containers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Container {

    @Id
    @Column(name = "container_no", length = 20, nullable = false)
    private String containerNo; // 컨테이너 고유 번호 (기본키)

    @Column(name = "company_id", nullable = false)
    private Long companyId; // 소속 업체 ID (companies 테이블 외래키)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reserved_cargo_info", columnDefinition = "jsonb")
    private String reservedCargoInfo; // 예약된 화물 상세 정보 (JSONB 포맷)

    // 외래키 매핑 (loading_locations 테이블의 location_id 참조)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loading_location_id", nullable = false)
    private LoadingLocation loadingLocation;

    @Column(name = "container_type", length = 20, nullable = false)
    private String containerType; // 컨테이너 타입 (냉동식품/위험물 등)

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 컨테이너 정보 생성 일시
}