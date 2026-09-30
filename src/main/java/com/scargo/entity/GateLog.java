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

    // 2026-09-30 변경: gate_name/gate_type을 문자열로 직접 저장하던 컬럼이
    // schema.sql 개편으로 사라지고, 게이트 마스터 테이블(gates)을 참조하는
    // gate_id(FK)로 바뀜에 따라 Gate 엔티티와의 다대일 관계로 교체함.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate; // 통과한 게이트 (gates FK)

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
     * 2026-09-30 변경: gate(게이트 마스터) 재배정은 gate_code로 DB 조회가
     * 필요해서 엔티티 안에서 처리할 수 없음 - GateLogService.updateGateLog()가
     * gateCode로 Gate를 조회한 뒤 setGate()로 직접 넣어줌(update()는 그 외
     * 필드만 담당).
     */
    public void update(GateLogUpdateRequest request) {
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
