package com.scargo.app.entity;

import java.util.List;

/** 26.10.03 2차: 운송오더 상태 (DB chk_order_status 와 값이 같아야 함) */
public enum OrderStatus {
    CREATED,      // 오더 생성(배차 전)
    ASSIGNED,     // 배차 완료
    LOADED,       // 기사가 컨테이너 적재 등록
    GATE_OUT,     // 터미널 OUT 게이트 통과
    IN_TRANSIT,   // 운행 중
    ARRIVED,      // HUB 도착
    HANDED_OVER,  // 인계 완료
    CANCELED; // fix_1300: DB 제약·웹·서버 SQL 과 같은 철자로 통일 (CANCELLED → CANCELED)

    /** 진행 중(완료·취소 아님) 상태 */
    public static final List<String> ACTIVE = List.of("CREATED", "ASSIGNED", "LOADED", "GATE_OUT", "IN_TRANSIT", "ARRIVED");
}
