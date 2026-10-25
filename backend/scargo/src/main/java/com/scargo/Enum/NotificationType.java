package com.scargo.Enum;

// 26.09.21 수정: DB의 chk_notification_type 체크 제약조건과 값이 어긋나 있던 문제 수정.
// (SYSTEM_ALERT로 저장 시도하면 "새 자료가 chk_notification_type 체크 제약 조건을 위반했습니다" 에러 발생)
// DB 제약조건: CHECK (notification_type IN ('CORPORATE_APPROVAL', 'OVERLOAD_WARNING', 'NOTICE', 'SYSTEM'))
public enum NotificationType {
    SYSTEM,             // 시스템 알림 (기존 SYSTEM_ALERT에서 이름 변경)
    NOTICE,             // 공지사항 (DB에는 있었지만 enum에 없던 값 추가)
    CORPORATE_APPROVAL, // 기업 승인
    OVERLOAD_WARNING,   // 과적 경고
    TIRE_ALERT,         // 26.09.30 병합: 타이어 점검/교체 주기 알림 (DB 체크 제약조건에도 추가 필요 - 병합_안내.md 참고)

    // 26.10.23 마이그레이션 14장(팀 DB v16 9-E 알림 유형). DB chk_notification_type 과 같은 목록
    //  만들기 · 조회 · 유형별 필터에 쓸 수 있다. 자동으로 만드는 기능(예외 · 반납 · 지선 등)은 아직 없음
    EXCEPTION,                  // 예외 발생
    DRIVER_APPROVAL_REQUEST,    // 기사 가입 승인 요청
    CONTAINER_RETURN_DUE,       // 공컨테이너 반납 기한 임박
    CONTAINER_RETURN_OVERDUE,   // 공컨테이너 반납 기한 초과
    DELAY_30MIN,                // 30분 이상 지연
    SEAL_OPEN_HOLD,             // 씰 개봉 보류
    PACKING_LIST_INVALID,       // 패킹리스트 오류
    BRANCH_DRIVER_UNASSIGNED,   // 지선 기사 미배정
    BRANCH_DOCK_UNLINKED        // 지선 출고 도크 미연결
}