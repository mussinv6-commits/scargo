package com.scargo.Enum;

// 26.09.21 수정: DB의 chk_notification_type 체크 제약조건과 값이 어긋나 있던 문제 수정.
// (SYSTEM_ALERT로 저장 시도하면 "새 자료가 chk_notification_type 체크 제약 조건을 위반했습니다" 에러 발생)
// DB 제약조건: CHECK (notification_type IN ('CORPORATE_APPROVAL', 'OVERLOAD_WARNING', 'NOTICE', 'SYSTEM'))
// 26.10.20 최종(DB chk_notification_type): 아래 14개. reference_id 규칙은 각 값 주석
public enum NotificationType {
    NOTICE,
    SYSTEM,
    OVERLOAD_WARNING,            // 과적 (reference_id = exception_id)
    CORPORATE_APPROVAL,          // 운송사(회사) 승인 결과 → company_id
    TIRE_ALERT,
    EXCEPTION,                   // 예외 일반 (reference_id = exception_id)
    DRIVER_APPROVAL_REQUEST,     // 기사 가입 승인 요청 (reference_id = 기사 account_id)
    CONTAINER_RETURN_DUE,        // 반납 기한 임박 (reference_id = order_id)
    CONTAINER_RETURN_OVERDUE,    // 반납 기한 초과 (reference_id = order_id)
    DELAY_30MIN,                 // 30분 이상 지연 (reference_id = assignment_id)
    SEAL_OPEN_HOLD,              // 씰 개봉 보류 (reference_id = exception_id)
    PACKING_LIST_INVALID,        // 패킹리스트 오류 (reference_id = exception_id, 없으면 shipment_id)
    BRANCH_DRIVER_UNASSIGNED,  // 지선기사 미배정 (reference_id = assignment_id)
    BRANCH_DOCK_UNLINKED,      // 지선 출고 도크 미연결 (reference_id = assignment_id)
    CONTAINER_RETURN_READY,      // v11 반납 가능 (허브 하역·공컨 점검 완료) → 운송사 (reference_id = order_id)
    CONTAINER_RETURN_INSTRUCTED, // v11 반납 지시 → 기사 (reference_id = order_id)
    RETURN_PLAN_CHANGED,         // v11 반납 CY·기한 변경 (reference_id = order_id)
    RETURN_CY_CHANGED,           // v11 반납 중 반납지 변경 → 기사 확인 필요 (reference_id = change_id)
    CONTAINER_DAMAGED            // v12 공컨 손상 기록 → 운송사 담당자 (reference_id = order_id)
}
