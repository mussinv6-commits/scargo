package com.scargo.entity;

import com.scargo.dto.GateLogUpdateRequest;
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
@Table(name = "gate_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gate_log_id", nullable = false)
    private Long gateLogId; // 게이트 통과 이력 ID (BIGSERIAL)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate; // 게이트 마스터 정보 연동 (외래 키)

    @Column(name = "recognized_plate_no", length = 20)
    private String recognizedPlateNo; // 전면 번호판 OCR 결과

    @Column(name = "recognized_trailer_no", length = 20)
    private String recognizedTrailerNo; // 후면/트레일러 번호판 OCR 결과

    @Column(name = "actual_vehicle_no", length = 20)
    private String actualVehicleNo; // 매칭된 차량 번호판 (trucks FK)

    @Column(name = "plate_confidence", precision = 5, scale = 2)
    private BigDecimal plateConfidence; // OCR 신뢰도 (0.00 ~ 100.00%)

    @Builder.Default
    @Column(name = "recognition_status", length = 20, nullable = false)
    private String recognitionStatus = "SUCCESS"; // 처리 상태 ('SUCCESS', 'FAILED')

    @Column(name = "front_image_url", columnDefinition = "TEXT")
    private String frontImageUrl; // 전면 이미지 URL

    @Column(name = "rear_image_url", columnDefinition = "TEXT")
    private String rearImageUrl; // 후면 이미지 URL

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ocr_raw_data", columnDefinition = "jsonb")
    private String ocrRawData; // OCR 원본 (JSONB 포맷)

    @Builder.Default
    @Column(name = "vehicle_type", length = 30)
    private String vehicleType = "UNKNOWN"; // 차종

    @Column(name = "pass_at", insertable = false, updatable = false)
    private OffsetDateTime passAt; // 통과 일시

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt; // 기록 생성 일시

    /*
     * 게이트 이력 수정 메서드 (도메인 비즈니스 로직)
     * Request DTO에서 null이 아닌 값이 전달된 필드만 수정
     */
    public void update(GateLogUpdateRequest request, Gate newGate) {
        // 게이트 자체가 변경된 경우 반영 (새로운 Gate 엔티티를 파라미터로 전달받는 구조 권장)
        if (newGate != null) {
            this.gate = newGate;
        }
        if (request.getRecognizedPlateNo() != null) {
            this.recognizedPlateNo = request.getRecognizedPlateNo();
        }
        if (request.getRecognizedTrailerNo() != null) {
            this.recognizedTrailerNo = request.getRecognizedTrailerNo();
        }
        if (request.getActualVehicleNo() != null) {
            this.actualVehicleNo = request.getActualVehicleNo();
        }
        if (request.getPlateConfidence() != null) {
            this.plateConfidence = request.getPlateConfidence();
        }
        if (request.getRecognitionStatus() != null) {
            this.recognitionStatus = request.getRecognitionStatus();
        }
        if (request.getFrontImageUrl() != null) {
            this.frontImageUrl = request.getFrontImageUrl();
        }
        if (request.getRearImageUrl() != null) {
            this.rearImageUrl = request.getRearImageUrl();
        }
        if (request.getOcrRawData() != null) {
            this.ocrRawData = request.getOcrRawData();
        }
        if (request.getVehicleType() != null) {
            this.vehicleType = request.getVehicleType();
        }
    }
}