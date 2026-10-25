package com.scargo.dto;

import com.scargo.entity.ContainerInspectionAudit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

// 26.10.23 컨테이너 점검표 변경 이력 1건
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInspectionAuditResponse {

    private Long auditId;
    private Long inspectionId;
    private String action;
    private Long actorAccountId;
    private String actorUserId;
    private Map<String, Object> beforeData;
    private Map<String, Object> afterData;
    private String reason;
    private OffsetDateTime createdAt;

    public ContainerInspectionAuditResponse(ContainerInspectionAudit a) {
        this(a.getAuditId(), a.getInspectionId(), a.getAction().name(), a.getActorAccountId(), a.getActorUserId(),
                a.getBeforeData(), a.getAfterData(), a.getReason(), a.getCreatedAt());
    }
}
