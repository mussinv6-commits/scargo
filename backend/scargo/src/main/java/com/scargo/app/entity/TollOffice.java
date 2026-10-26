package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 26.10.03 2차: 고속도로 영업소 - 출처: 전국영업소.json */
@Entity
@Table(name = "tollgates")   // 26.10.20: 11차에서 toll_offices → tollgates (tollgate_code 를 식별자로 사용)
@Getter
@Setter
@NoArgsConstructor
public class TollOffice {
    @Id
    @Column(name = "tollgate_code", length = 20)
    private String officeCode;

    @Column(name = "tollgate_name", nullable = false, length = 100)
    private String officeName;

    @Column(name = "route_no", length = 10)
    private String routeNo;

    @Column(name = "road_name", length = 100)
    private String routeName;

    @Column(name = "sido", length = 20)
    private String sido;

    @Column(name = "sigungu", length = 30)
    private String sigungu;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "pass_radius_m", nullable = false)
    private Integer passRadiusM = 300;
}
