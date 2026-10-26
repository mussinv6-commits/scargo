package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.15 KPI: amr_task_logs (AMR 운반 1건 = 1행 · 처리량 · 운반 시간 · 교차로 대기 집계용)
@Entity
@Table(name = "amr_task_logs")
@Getter
@Setter
@NoArgsConstructor
public class AmrTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "robot_code", length = 20, nullable = false)
    private String robotCode;

    @Column(name = "task_type", length = 10, nullable = false)
    private String taskType; // IN(입고 → 랙) / OUT(랙 → 지선차량)

    @Column(name = "waybill_no", length = 30)
    private String waybillNo;

    @Column(name = "barcode", length = 13)
    private String barcode; // 상자 EAN-13

    @Column(name = "dest_hub", length = 20)
    private String destHub;

    @Column(name = "handle_type", length = 20)
    private String handleType;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "from_loc", length = 30)
    private String fromLoc;

    @Column(name = "to_loc", length = 30)
    private String toLoc;

    @Column(name = "duration_sec")
    private Double durationSec;

    @Column(name = "inter_wait_sec")
    private Double interWaitSec; // 이 운반 중 교차로에서 기다린 시간

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;
}
