package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.15 관리자 운영 기준 (허브별 1줄) — 정비 기준 · 충전 기준
//  amr_settings 테이블은 백엔드 실행 시 자동 생성 (ddl-auto: update)
@Entity
@Table(name = "amr_settings")
@Getter
@Setter
@NoArgsConstructor
public class AmrSetting {

    @Id
    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "rul_threshold_hours", nullable = false)
    private Integer rulThresholdHours = 24; // 남은 수명이 이 시간 이하면 자동 정비 예약

    @Column(name = "charge_at_pct", nullable = false)
    private Integer chargeAtPct = 30; // 배터리가 이 % 이하면 작업 후 충전소 이동

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
