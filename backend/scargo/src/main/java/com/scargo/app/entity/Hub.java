package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 전국 HUB · 물류창고 (목적지) - 출처: 전국 물류창고 지도.json */
@Entity
@Table(name = "hubs")
@Getter
@Setter
@NoArgsConstructor
public class Hub {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hub_id")
    private Long hubId;

    // 26.10.20 11차 hubs 에 맞춤: center_id NOT NULL UNIQUE (logistics_centers, center_type HUB).
    //  좌표·주소는 hubs 에 없고 logistics_centers 에서 가져온다 (아래 getLatitude 등)
    @Column(name = "center_id", nullable = false, unique = true)
    private Long centerId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "center_id", insertable = false, updatable = false)
    private com.scargo.ops.entity.LogisticsCenter center;

    @Column(name = "hub_code", nullable = false, unique = true, length = 30)
    private String hubCode;

    @Column(name = "hub_name", nullable = false, length = 100)
    private String hubName;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "source_id", unique = true, length = 50)
    private String sourceId;

    @Column(name = "hub_type", nullable = false, length = 20)
    private String hubType = "물류창고";   // 물류창고 / 물류터미널

    @Column(name = "jibun_address", columnDefinition = "TEXT")
    private String jibunAddress;

    @Column(name = "total_area_m2", precision = 12, scale = 2)
    private BigDecimal totalAreaM2;

    @Column(name = "cold_storage_area_m2", precision = 12, scale = 2)
    private BigDecimal coldStorageAreaM2;

    @Column(name = "size_class", length = 10)
    private String sizeClass;

    @Column(name = "is_mega", nullable = false)
    private Boolean isMega = false;

    @Column(name = "geofence_radius_m", nullable = false)
    private Integer geofenceRadiusM = 300;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // ---- 1차/2차 초안 코드 호환 (이름·좌표는 11차 위치에서 읽는다) ----
    public String getBusinessName() { return hubName; }
    public BigDecimal getLatitude() { return center == null ? null : center.getLatitude(); }
    public BigDecimal getLongitude() { return center == null ? null : center.getLongitude(); }
    public String getRoadAddress() { return center == null ? null : center.getAddress(); }
}
