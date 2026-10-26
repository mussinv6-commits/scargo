package com.scargo.ops.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * v13: 허브 출고 도크 상태(hub_docks.status) 갱신. DB 에 계산 트리거·뷰가 없어 백엔드가 맞춘다.
 *  - 도크에 연결된 지선 운행 중 아직 출발 전(ASSIGNED, hub_dispatches.departed_at NULL) 이 있으면 DOCKED
 *  - 없고, 출발해서 운행 중(IN_TRANSIT) 인 운행이 있으면 LEAVING
 *  - 둘 다 없으면(도착했거나 연결 해제·취소) EMPTY
 * 호출한 쪽 트랜잭션 안에서 실행(MANDATORY) → 운행 변경과 도크 상태가 함께 커밋/롤백된다.
 */
@Service
@RequiredArgsConstructor
public class DockStatusService {
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    public String refresh(Long dockId) {
        if (dockId == null) return null;
        em.flush(); // 같은 트랜잭션의 JPA 변경(운행 상태·dispatch)을 먼저 반영한 뒤 계산
        String st = jdbc.queryForObject("""
            SELECT CASE
                     WHEN EXISTS (SELECT 1 FROM hub_dispatches d JOIN shipment_legs l ON l.assignment_id = d.assignment_id
                                   WHERE d.dock_id = ? AND l.status = 'ASSIGNED' AND d.departed_at IS NULL) THEN 'DOCKED'
                     WHEN EXISTS (SELECT 1 FROM hub_dispatches d JOIN shipment_legs l ON l.assignment_id = d.assignment_id
                                   WHERE d.dock_id = ? AND l.status = 'IN_TRANSIT') THEN 'LEAVING'
                     ELSE 'EMPTY' END""", String.class, dockId, dockId);
        jdbc.update("UPDATE hub_docks SET status = ?, updated_at = now() WHERE dock_id = ? AND status IS DISTINCT FROM ?", st, dockId, st);
        return st;
    }

    /** 운행에 연결된 출고 도크 갱신 */
    @Transactional(propagation = Propagation.MANDATORY)
    public String refreshForTrip(Long tripId) {
        em.flush();
        java.util.List<Long> d = jdbc.queryForList("SELECT dock_id FROM hub_dispatches WHERE assignment_id = ? AND dock_id IS NOT NULL", Long.class, tripId);
        String last = null;
        for (Long id : d) last = refresh(id);
        return last;
    }
}
