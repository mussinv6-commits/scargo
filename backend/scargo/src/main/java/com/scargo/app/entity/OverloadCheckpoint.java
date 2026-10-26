package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 26.10.03 2차: 과적검문소 - 출처: 전국 과적검문소.json */
@Entity
@Table(name = "weigh_stations")   // 26.10.20: 11차에서 overload_checkpoints → weigh_stations
@Getter
@Setter
@NoArgsConstructor
public class OverloadCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "station_id")
    private Long checkpointId;

    @Column(name = "station_code", nullable = false, unique = true, length = 30)
    private String checkpointCode;

    @Column(name = "station_name", nullable = false, length = 100)
    private String checkpointName;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "road_name", length = 100)   // 11차: 도로명(노선)
    private String roadAddress;

    @Column(name = "sido", length = 20)
    private String sido;

    @Column(name = "sigungu", length = 30)
    private String sigungu;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "alert_radius_m", nullable = false)
    private Integer alertRadiusM = 2000;
}
