package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 26.10.14 AMR 연동: 허브 마스터 (manual_merge_amr_hub_261014.sql 의 hubs 테이블, 조회만)
@Entity
@Table(name = "hubs")
@Getter
@NoArgsConstructor
public class Hub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "hub_code", length = 30, nullable = false)
    private String hubCode; // HUB-ICH-01

    @Column(name = "hub_name", length = 100, nullable = false)
    private String hubName;
}
