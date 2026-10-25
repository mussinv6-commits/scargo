package com.scargo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

// 26.10.23 허브 도착 · 인계 화면의 오더 1건 (운송 구간 · 입고 · 인계 상태 + 입차 OCR 근거)
//  기준 씰 번호는 내려주지 않는다 (인계 때 현장에서 읽은 번호와 비교하는 값이라 화면에 보이면 확인이 무의미)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HubArrivalCandidateResponse {

    private Long orderId;
    private String orderNo;
    private String orderStatus;
    private Long companyId;
    private String containerNo;
    private String vehicleNo;
    private Long destHubId;
    private OffsetDateTime assignedAt;
    private OffsetDateTime arrivedAt;
    private OffsetDateTime handedOverAt;

    private Long assignmentId;
    private String legStatus;

    private Long receiptId;
    private String receiptStatus;
    private Long dockId;
    private OffsetDateTime receiptArrivedAt;

    private Long handoverId;
    private OffsetDateTime handoverCompletedAt;

    private boolean sealRegistered;   // 오더 컨테이너에 ACTIVE 씰이 있는지
    private boolean canArrive;        // 지금 도착 확정 가능 (상태 · 입고 기록 · OCR 근거 기준)
    private boolean canHandover;      // 지금 인계 확정 가능 (상태 · 입고 기록 · 인계 기록 기준)

    private List<OcrEvidence> ocrEvidence;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OcrEvidence {
        private Long logId;
        private String recognizedPlate;
        private String decision;
        private Double ocrConfidence;
        private Integer dockNo;
        private String sealStatus;
        private OffsetDateTime createdAt;
    }
}
