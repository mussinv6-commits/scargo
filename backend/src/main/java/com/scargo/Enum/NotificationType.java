package com.scargo.Enum;

// 26.09.21 수정: DB의 chk_notification_type 체크 제약조건과 값이 어긋나 있던 문제 수정.
// (SYSTEM_ALERT로 저장 시도하면 "새 자료가 chk_notification_type 체크 제약 조건을 위반했습니다" 에러 발생)
// DB 제약조건: CHECK (notification_type IN ('CORPORATE_APPROVAL', 'OVERLOAD_WARNING', 'NOTICE', 'SYSTEM'))
public enum NotificationType {
    SYSTEM,             // 시스템 알림 (기존 SYSTEM_ALERT에서 이름 변경)
    NOTICE,             // 공지사항 (DB에는 있었지만 enum에 없던 값 추가)
    CORPORATE_APPROVAL, // 기업 승인
    OVERLOAD_WARNING,   // 과적 경고
    TIRE_ALERT          // 26.09.30 병합: 타이어 점검/교체 주기 알림 (DB 체크 제약조건에도 추가 필요 - 병합_안내.md 참고)
}