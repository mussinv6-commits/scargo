package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.15 허브 입출차: hub_vehicle_logs (입구 · 출구 번호판 OCR 기록)
//  decision: ALLOW(입차 허용) / BLOCK(미등록 · 판독불가 차단) / EXIT_OK(출차 확인) / MISMATCH(출차 번호 불일치)
@Entity
@Table(name = "hub_vehicle_logs")
@Getter
@Setter
@NoArgsConstructor
public class HubVehicleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "direction", length = 10, nullable = false)
    private String direction; // ENTRY / EXIT

    @Column(name = "recognized_plate", length = 20)
    private String recognizedPlate; // OCR 결과 (판독불가면 null)

    @Column(name = "photo_plate", length = 20)
    private String photoPlate; // 사진 파일명 = 실제 번호판 (인식 정확도 비교용)

    @Column(name = "vehicle_kind", length = 10)
    private String vehicleKind; // CARGO / COURIER / UNKNOWN

    @Column(name = "decision", length = 10, nullable = false)
    private String decision;

    @Column(name = "lane", length = 5)
    private String lane; // IN(입고 도크 쪽 · 화물차 레인) / OUT(출고 도크 쪽 · 택배차 레인)

    @Column(name = "dock_no")
    private Integer dockNo;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "ocr_engine", length = 20)
    private String ocrEngine;

    @Column(name = "image_url", length = 300)
    private String imageUrl;

    @Column(name = "box_count")
    private Integer boxCount;

    @Column(name = "barcodes", columnDefinition = "TEXT")
    private String barcodes; // 출차 때 실린 상자 EAN-13 (쉼표 구분)

    @Column(name = "dest_hub", length = 20)
    private String destHub;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
