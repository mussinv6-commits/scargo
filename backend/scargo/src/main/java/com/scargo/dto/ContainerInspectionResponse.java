package com.scargo.dto;

import com.scargo.entity.ContainerInspection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

// 26.10.23 컨테이너 점검표 응답. can* 는 지금 로그인한 계정이 할 수 있는 동작 (화면 버튼 표시용, 서버가 다시 확인)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerInspectionResponse {

    private Long inspectionId;
    private Long orderId;
    private String orderNo;
    private String containerNo;
    private Long driverAccountId;
    private Long companyId;
    private Long hubId;
    private Map<String, Boolean> items;
    private String overallStatus;
    private String recordStatus;
    private String memo;
    private Long previousInspectionId;
    private Long createdByAccountId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long finalizedByAccountId;
    private OffsetDateTime finalizedAt;
    private Long cancelledByAccountId;
    private OffsetDateTime cancelledAt;
    private String cancelReason;

    private boolean canEdit;
    private boolean canCancel;
    private boolean canReinspect;

    public static ContainerInspectionResponseBuilder from(ContainerInspection c) {
        return ContainerInspectionResponse.builder()
                .inspectionId(c.getInspectionId())
                .orderId(c.getOrderId())
                .containerNo(c.getContainerNo())
                .driverAccountId(c.getDriverAccountId())
                .companyId(c.getCompanyId())
                .hubId(c.getHubId())
                .items(c.getItems())
                .overallStatus(c.getOverallStatus())
                .recordStatus(c.getRecordStatus())
                .memo(c.getMemo())
                .previousInspectionId(c.getPreviousInspectionId())
                .createdByAccountId(c.getCreatedByAccountId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .finalizedByAccountId(c.getFinalizedByAccountId())
                .finalizedAt(c.getFinalizedAt())
                .cancelledByAccountId(c.getCancelledByAccountId())
                .cancelledAt(c.getCancelledAt())
                .cancelReason(c.getCancelReason());
    }
}
