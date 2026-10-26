package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 logistics_centers. center_type: PORT | HUB | DISTRIBUTION_CENTER */
@Entity @Table(name = "logistics_centers")
@Getter @Setter @NoArgsConstructor
public class LogisticsCenter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "center_id") private Long centerId;
    @Column(name = "center_code", length = 50, nullable = false, unique = true) private String centerCode;
    @Column(name = "center_name", length = 100, nullable = false) private String centerName;
    @Column(name = "center_type", length = 20, nullable = false) private String centerType;
    @Column(name = "address", columnDefinition = "TEXT") private String address;
    @Column(name = "latitude", precision = 10, scale = 7) private BigDecimal latitude;
    @Column(name = "longitude", precision = 10, scale = 7) private BigDecimal longitude;
    @Column(name = "region_id") private Long regionId;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
}
