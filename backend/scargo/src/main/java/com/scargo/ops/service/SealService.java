package com.scargo.ops.service;

import com.scargo.app.entity.TransportOrder;
import com.scargo.app.repository.TransportOrderRepository;
import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.ops.entity.*;
import com.scargo.ops.repository.*;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** 26.10.20 씰 번호 앱 → 웹 연동.
 *  - 컨테이너 기사 T-02: 입력 씰 → seal_events(INSPECTED, order_id) / 불일치 → SEAL_MISMATCH 예외
 *  - 지선기사 출발: shipment_legs.domestic_seal_no (BranchService.depart)
 *  - 허브 씰 확인: seal_events(INSPECTED, center_id) (HubOpsService.receive)
 *  웹(W-02·W-04·W-09)은 GET /api/web/seals 와 monitor 응답으로 같은 데이터를 읽는다 */
@Service
@RequiredArgsConstructor
public class SealService {
    private final ContainerSealRepository seals;
    private final SealEventRepository events;
    private final ShipmentLegRepository legs;
    private final TransportOrderRepository orders;
    private final AccountRepository accounts;
    private final ExceptionService exceptionService;
    private final OpsNotifier notifier;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc; // fix_1100: 오더의 shipment_id 조회

    /** 컨테이너 기사: 내 진행 중 오더 + 그 컨테이너의 활성 씰(기대값) */
    public Map<String, Object> myCurrentOrder() {
        // fix_1400: T-02 는 인수 대기(ASSIGNED) 오더를 우선 (없을 때만 LOADED)
        List<TransportOrder> mine = orders.findByDriverAccountIdAndStatusInOrderByAssignedAtAsc(CurrentAccount.id(), List.of("ASSIGNED", "LOADED"));
        TransportOrder o = mine.stream().filter(x -> "ASSIGNED".equals(x.getStatus())).findFirst().orElse(mine.stream().findFirst().orElse(null));
        if (o == null) return Map.of("order", Map.of());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getOrderId()); m.put("orderNo", o.getOrderNo()); m.put("containerNo", o.getContainerNo()); m.put("status", o.getStatus());
        ContainerSeal s = activeSeal(o.getContainerNo());
        m.put("sealId", s == null ? null : s.getSealId());
        m.put("sealRegistered", s != null); // fix_1400: 정답 씰 번호는 기사에게 내려주지 않음 (등록 여부만)
        return Map.of("order", m);
    }

    /** T-02 인수 확인: 입력한 컨테이너 번호·씰번호 기록. 일치하면 오더 LOADED, 불일치면 예외 + 개봉 보류 */
    @Transactional
    public Map<String, Object> containerPickup(Long orderId, String containerNo, String sealNo, boolean checksOk) {
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        TransportOrder o = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("오더가 없습니다."));
        if (!Objects.equals(o.getDriverAccountId(), me.getAccountId())) throw new IllegalStateException("내 오더가 아닙니다.");
        if (containerNo == null || containerNo.isBlank()) containerNo = o.getContainerNo(); // fix_1517: 앱이 안 보내면 오더 값
        if (containerNo == null || !containerNo.trim().equalsIgnoreCase(o.getContainerNo())) throw new IllegalArgumentException("오더의 컨테이너 번호와 다릅니다.");
        if (sealNo == null || sealNo.isBlank()) throw new IllegalArgumentException("씰번호를 입력하세요.");
        ContainerSeal s = activeSeal(o.getContainerNo());
        // fix_1330 씰 정책: 정답 씰은 DB 에 미리 등록된(시드) container_seals 만. 기사 입력·다른 경로로 정답 씰을 만들지 않는다.
        //  정답 씰이 없으면 인수 거절 (오더는 ASSIGNED 그대로, 기록·예외 없음). 앱은 이 메시지를 모달로 표시
        if (s == null || !Objects.equals(s.getShipmentId(), shipmentOf(o.getOrderId())))
            throw new SealNotRegisteredException();
        SealEvent ev = inspect(o, s, sealNo.trim(), me, hubCenter(o), "컨테이너 기사 T-02 인수 확인"); // fix_1330: 도착 허브 거점으로 기록 (W-09 처리 권한과 일치)
        OpsException x = exceptionService.fromSealEvent(ev);
        boolean match = x == null;
        if (!match) {
            s.setOpenHold(true);
            notifyMismatch(o, s.getContainerNo(), x);
        } else {
            autoRelease(o, s, ev, me); // fix_1345 → fix_1400: T-02 에서 같은 기사가 낸 불일치만
        }
        if (match && checksOk && "ASSIGNED".equals(o.getStatus())) {
            o.setStatus("LOADED");   // TODO(기획): 기획 v2 의 PICKED_UP 은 1차 OrderStatus 에 없음 → 1차 LOADED(적재 등록) 로 기록
            o.setLoadedAt(OffsetDateTime.now());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("match", match); r.put("result", match ? "MATCH" : "MISMATCH"); r.put("eventId", ev.getEventId()); r.put("exceptionId", x == null ? null : x.getExceptionId());
        r.put("orderStatus", o.getStatus()); r.put("openHold", s.getOpenHold());
        return r;
    }

    /** fix_1300: 허브 인계(handovers.seal_no) 대조. 정답 씰과 비교 → 불일치면 SEAL_MISMATCH(개봉 보류), 정답 없으면 미등록 기록.
     *  반환: MATCH | MISMATCH | UNREGISTERED | NONE(입력 없음). 정답 씰이 있으면 handovers.seal_id/seal_shipment_id 도 채운다 */
    @Transactional
    public String handoverSeal(TransportOrder o, com.scargo.app.entity.Handover h, Long actorAccountId) {
        String in = h.getSealNo() == null ? null : h.getSealNo().trim().toUpperCase();
        if (in == null || in.isEmpty()) return "NONE";
        Account me = accounts.findById(actorAccountId).orElseThrow();
        ContainerSeal s = activeSeal(o.getContainerNo());
        if (s == null || !Objects.equals(s.getShipmentId(), shipmentOf(o.getOrderId()))) { unregistered(o, in, me, "HANDOVER"); return "UNREGISTERED"; }
        h.setSealId(s.getSealId()); h.setSealShipmentId(s.getShipmentId());
        SealEvent ev = inspect(o, s, in, me, hubCenter(o), "허브 인계 씰 확인 (handovers)");
        OpsException x = exceptionService.fromSealEvent(ev);
        if (x == null) return "MATCH"; // fix_1400: 인계 단계 일치로는 개봉 보류 자동 해제 안 함 (HUB_OP 재인증으로만 해제)
        s.setOpenHold(true);
        notifyMismatch(o, s.getContainerNo(), x);
        return "MISMATCH";
    }

    private SealEvent inspect(TransportOrder o, ContainerSeal s, String observed, Account me, Long centerId, String memo) {
        SealEvent ev = new SealEvent();
        ev.setSealId(s.getSealId()); ev.setShipmentId(s.getShipmentId()); ev.setOrderId(o.getOrderId()); ev.setExpectedSealNo(s.getSealNo());
        ev.setEventType("INSPECTED"); ev.setObservedCondition("INTACT"); ev.setObservedSealNo(observed); ev.setCenterId(centerId);
        ev.setActorAccountId(me.getAccountId()); ev.setActorName(me.getUserName()); ev.setOccurredAt(OffsetDateTime.now());
        ev.setMemo(memo);
        return events.saveAndFlush(ev);
    }

    /** 정답 씰 미등록: 입력값 보존 + WARNING 예외 (오더당 1건) + 운송사 알림 */
    private Long unregistered(TransportOrder o, String observed, Account me, String stage) {
        Long center = hubCenter(o);
        OpsException x = exceptionService.raise("SEAL_UNREGISTERED", "WARNING", center, null, "transport_orders", String.valueOf(o.getOrderId()),
                "정답 씰 미등록 · " + o.getContainerNo() + " · 현장 입력 " + observed + " (" + ("HANDOVER".equals(stage) ? "허브 인계" : "기사 인수") + ")", null);
        Long xid = x != null ? x.getExceptionId() : jdbc.query("SELECT exception_id FROM exceptions WHERE source_table = 'transport_orders' AND source_key = ? AND exception_type = 'SEAL_UNREGISTERED'",
                rs -> rs.next() ? rs.getLong(1) : null, String.valueOf(o.getOrderId()));
        jdbc.update("INSERT INTO seal_unregistered_inputs (order_id, container_no, observed_seal_no, stage, actor_account_id, actor_name, exception_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                o.getOrderId(), o.getContainerNo(), observed, stage, me.getAccountId(), me.getUserName(), xid);
        if (x != null) notifier.toCompany(o.getCompanyId(), com.scargo.Enum.NotificationType.EXCEPTION, "[정답 씰 미등록] " + o.getContainerNo(), x.getMessage(), xid);
        return xid;
    }

    /** fix_1345 → fix_1400: T-02 에서 '같은 기사'가 낸 불일치(SEAL_MISMATCH) 뒤 그 기사가 T-02 에서 정답을 다시 입력한 경우에만
     *  해당 예외 자동 해결 + (그 씰에 다른 열린 불일치가 없으면) 개봉 보류 해제 + 해제 알림.
     *  허브(H-01)·인계 단계에서 생긴 불일치는 건드리지 않음 → HUB_OP 비밀번호 재인증 개봉 승인으로만 해제.
     *  분실(SEAL_LOST)·손상(SEAL_DAMAGED) 예외도 건드리지 않음 */
    private static final String T02_MEMO = "컨테이너 기사 T-02 인수 확인";
    private void autoRelease(TransportOrder o, ContainerSeal s, SealEvent matched, Account me) {
        final String note = "기사 재입력 일치 (T-02 인수 확인, 같은 기사 " + me.getUserName() + ")";
        List<Long> open = jdbc.queryForList("""
            SELECT e.exception_id FROM exceptions e JOIN seal_events v ON e.source_table = 'seal_events' AND e.source_key = v.event_id::text
             WHERE v.seal_id = ? AND v.order_id = ? AND v.actor_account_id = ? AND v.memo = ?
               AND e.exception_type = 'SEAL_MISMATCH' AND e.status <> 'RESOLVED'""", Long.class, s.getSealId(), o.getOrderId(), me.getAccountId(), T02_MEMO);
        if (open.isEmpty()) return;
        for (Long id : open) {
            String key = jdbc.queryForObject("SELECT source_key FROM exceptions WHERE exception_id = ?", String.class, id);
            exceptionService.resolveBySource("seal_events", key, note);
        }
        Integer others = jdbc.queryForObject("""
            SELECT count(*) FROM exceptions e JOIN seal_events v ON e.source_table = 'seal_events' AND e.source_key = v.event_id::text
             WHERE v.seal_id = ? AND e.exception_type IN ('SEAL_MISMATCH','SEAL_DAMAGED','SEAL_LOST') AND e.status <> 'RESOLVED'
               AND e.exception_id NOT IN (""" + open.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + ")", Integer.class, s.getSealId()); // 방금 해결한 건(아직 flush 전) 제외
        if (others != null && others > 0) return; // 다른 경로의 열린 씰 예외가 있으면 보류 유지
        s.setOpenHold(false);
        var t = com.scargo.Enum.NotificationType.SEAL_OPEN_HOLD;
        String title = "[씰 개봉 보류 자동 해제] " + s.getContainerNo();
        Map<String, Object> first = jdbc.query("""
            SELECT v.observed_seal_no, v.occurred_at FROM exceptions e JOIN seal_events v ON e.source_table = 'seal_events' AND e.source_key = v.event_id::text
             WHERE e.exception_id = ?""",
            rs -> rs.next() ? Map.<String, Object>of("no", rs.getString(1), "at", rs.getObject(2, OffsetDateTime.class)) : null, open.get(0));
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(java.time.ZoneId.of("Asia/Seoul"));
        String msg = s.getContainerNo() + " 자동 해제 · " + note + " · 처음 입력 " + (first == null ? "-" : first.get("no") + " (" + hm.format((OffsetDateTime) first.get("at")) + ")")
                + " → 재입력 " + matched.getObservedSealNo() + " (" + hm.format(matched.getOccurredAt()) + ") · 정답 씰 " + s.getSealNo();
        Long ref = open.get(0);
        notifier.toOps(null, t, title, msg, ref);
        notifier.toCompany(o.getCompanyId(), t, title, msg, ref);
        if (o.getDestHubId() != null)
            for (Account a : accounts.findAll())
                if (a.getUserType() == Account.UserType.HUB_OP && Objects.equals(a.getHubId(), o.getDestHubId()))
                    notifier.toAccount(a.getAccountId(), t, title, msg, ref);
    }

    /** fix_1300: 씰 불일치 알림 = 관제(CONTROL) + 오더 운송사 + 도착 허브 HUB_OP */
    private void notifyMismatch(TransportOrder o, String containerNo, OpsException x) {
        var t = com.scargo.Enum.NotificationType.SEAL_OPEN_HOLD;
        String title = "[씰 불일치] " + containerNo;
        notifier.toOps(null, t, title, x.getMessage(), x.getExceptionId());
        notifier.toCompany(o.getCompanyId(), t, title, x.getMessage(), x.getExceptionId());
        if (o.getDestHubId() != null)
            for (Account a : accounts.findAll())
                if (a.getUserType() == Account.UserType.HUB_OP && Objects.equals(a.getHubId(), o.getDestHubId()))
                    notifier.toAccount(a.getAccountId(), t, title, x.getMessage(), x.getExceptionId());
    }

    private Long hubCenter(TransportOrder o) {
        if (o.getDestHubId() == null) return null;
        return jdbc.query("SELECT center_id FROM hubs WHERE hub_id = ?", rs -> rs.next() ? rs.getLong(1) : null, o.getDestHubId());
    }

    private Long shipmentOf(Long orderId) {
        return jdbc.query("SELECT shipment_id FROM transport_orders WHERE order_id = ?", rs -> rs.next() ? (Long) rs.getObject(1, Long.class) : null, orderId);
    }

    private ContainerSeal activeSeal(String containerNo) {
        return seals.findByContainerNoAndStatus(containerNo, "ACTIVE").stream().findFirst().orElse(null);
    }

    /** 승인 운송사 담당자면 자기 회사(transport_orders.company_id 의 컨테이너, trucks.company_id 의 지선)만 */
    public Map<String, Object> overviewForMe() {
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        Map<String, Object> all = overview();
        // fix_1530: HUB_OP 는 소속 허브로 오는 오더(dest_hub_id)의 컨테이너 씰만 (ADMIN·CONTROL 은 전체)
        if (me.getUserType() == Account.UserType.HUB_OP && me.getHubId() != null) {
            Set<String> hubC = new HashSet<>(jdbc.queryForList("SELECT container_no FROM transport_orders WHERE dest_hub_id = ? AND container_no IS NOT NULL", String.class, me.getHubId()));
            Map<String, Object> r = new LinkedHashMap<>(all);
            r.put("containerSeals", ((List<Map<String, Object>>) all.get("containerSeals")).stream().filter(x -> hubC.contains(x.get("containerNo"))).toList());
            return r;
        }
        if (!com.scargo.ops.CarrierLegacy.isApprovedCarrierContact(me)) return all;
        Long c = me.getCompanyId();
        Set<String> containers = new HashSet<>();
        for (TransportOrder o : orders.findByCompanyIdOrderByOrderIdDesc(c)) containers.add(o.getContainerNo());
        Map<String, Object> r = new LinkedHashMap<>(all);
        r.put("containerSeals", ((List<Map<String, Object>>) all.get("containerSeals")).stream().filter(x -> containers.contains(x.get("containerNo"))).toList());
        r.put("unregistered", ((List<Map<String, Object>>) all.get("unregistered")).stream().filter(x -> containers.contains(x.get("containerNo"))).toList());
        r.put("branchSeals", ((List<Map<String, Object>>) all.get("branchSeals")).stream().filter(x -> java.util.Objects.equals(notifier.companyOfVehicle((String) x.get("vehicleNo")), c)).toList());
        return r;
    }

    /** fix_1630: W-07 오더별 씰 확인 이력 (seal_events.order_id + 미등록 입력). 운송사는 자기 회사 오더, HUB_OP 는 소속 허브행 오더만 */
    public Map<String, Object> orderSealHistory(Long orderId) {
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        TransportOrder o = orders.findById(orderId).orElseThrow(() -> new IllegalArgumentException("오더가 없습니다."));
        if (me.getUserType() == Account.UserType.HUB_OP && me.getHubId() != null && !Objects.equals(me.getHubId(), o.getDestHubId()))
            throw new org.springframework.security.access.AccessDeniedException("소속 허브 오더만 볼 수 있습니다.");
        if (me.getUserType() == Account.UserType.CORPORATE_APPROVED) {
            if (!com.scargo.ops.CarrierLegacy.isApprovedCarrierContact(me) || !Objects.equals(me.getCompanyId(), o.getCompanyId()))
                throw new org.springframework.security.access.AccessDeniedException("자기 회사 오더만 볼 수 있습니다.");
        }
        List<Map<String, Object>> ev = events.findByOrderIdOrderByOccurredAtDesc(orderId).stream().map(e -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("eventId", e.getEventId()); x.put("type", e.getEventType()); x.put("observedSealNo", e.getObservedSealNo());
            x.put("condition", e.getObservedCondition()); x.put("matches", ExceptionService.matches(e));
            x.put("actor", e.getActorName()); x.put("at", e.getOccurredAt()); x.put("memo", e.getMemo());
            return x;
        }).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderId", orderId); r.put("containerNo", o.getContainerNo()); r.put("events", ev);
        r.put("unregistered", jdbc.queryForList("""
            SELECT u.input_id AS "inputId", u.observed_seal_no AS "observedSealNo", u.stage, u.actor_name AS "actorName", u.occurred_at AS "at"
              FROM seal_unregistered_inputs u WHERE u.order_id = ? ORDER BY u.input_id DESC""", orderId));
        return r;
    }

    /** 웹/허브 앱 공용 씰 현황: 씰별 최신 확인 이벤트 + 지선 국내 봉인 */
    public Map<String, Object> overview() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ContainerSeal s : seals.findAllByOrderBySealIdDesc()) list.add(sealView(s));
        List<Map<String, Object>> lh = new ArrayList<>();
        for (ShipmentLeg l : legs.findAll()) {
            if (!BranchService.LEG_TYPE.equals(l.getLegType()) || l.getDomesticSealNo() == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tripId", l.getAssignmentId()); m.put("vehicleNo", l.getVehicleNo()); m.put("toCenterId", l.getToCenterId());
            m.put("domesticSealNo", l.getDomesticSealNo()); m.put("departedAt", l.getDepartedAt()); m.put("status", l.getStatus());
            // fix_1300: 센터 도착 확인 봉인과 비교 (null = 아직 확인 전)
            m.put("arrivalSealNo", l.getArrivalSealNo()); m.put("arrivalCheckedAt", l.getArrivalSealCheckedAt()); m.put("arrivalCheckedBy", l.getArrivalSealCheckedBy());
            m.put("arrivalMatches", l.getArrivalSealNo() == null ? null : l.getArrivalSealNo().equals(l.getDomesticSealNo()));
            lh.add(m);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("containerSeals", list); r.put("branchSeals", lh); r.put("serverTime", OffsetDateTime.now());
        // fix_1300: 정답 씰 미등록 입력 (판정 '미등록')
        r.put("unregistered", jdbc.queryForList("""
            SELECT u.input_id AS "inputId", u.order_id AS "orderId", o.order_no AS "orderNo", u.container_no AS "containerNo", u.observed_seal_no AS "observedSealNo",
                   u.stage, u.actor_name AS "actorName", u.occurred_at AS "at", o.dest_hub_id AS "hubId"
              FROM seal_unregistered_inputs u JOIN transport_orders o ON o.order_id = u.order_id
             WHERE NOT EXISTS (SELECT 1 FROM container_seals s WHERE s.container_no = u.container_no AND s.status = 'ACTIVE')
             ORDER BY u.input_id DESC LIMIT 100"""));
        return r;
    }

    public Map<String, Object> sealView(ContainerSeal s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sealId", s.getSealId()); m.put("shipmentId", s.getShipmentId()); m.put("containerNo", s.getContainerNo());
        m.put("expectedSealNo", s.getSealNo()); m.put("status", s.getStatus()); m.put("openHold", s.getOpenHold()); m.put("removedAt", s.getRemovedAt());
        List<SealEvent> ev = events.findBySealIdOrderByOccurredAtDesc(s.getSealId());
        SealEvent last = ev.stream().filter(e -> !"OPENED".equals(e.getEventType())).findFirst().orElse(null);
        m.put("lastObservedSealNo", last == null ? null : last.getObservedSealNo());
        m.put("lastCondition", last == null ? null : last.getObservedCondition());
        m.put("lastMatches", last == null ? null : ExceptionService.matches(last));
        m.put("lastBy", last == null ? null : last.getActorName());
        m.put("lastMemo", last == null ? null : last.getMemo()); // fix_1300: 어느 단계 확인인지 (기사 인수 / 허브 인계 / 도크)
        m.put("lastAt", last == null ? null : last.getOccurredAt());
        m.put("events", ev.stream().limit(10).map(e -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("eventId", e.getEventId()); x.put("type", e.getEventType()); x.put("observedSealNo", e.getObservedSealNo());
            x.put("condition", e.getObservedCondition()); x.put("matches", ExceptionService.matches(e)); x.put("orderId", e.getOrderId());
            x.put("centerId", e.getCenterId()); x.put("actor", e.getActorName()); x.put("at", e.getOccurredAt());
            x.put("memo", e.getMemo()); // fix_1530: 입력 단계 (기사 T-02 인수 / 허브 인계 / 허브 H-01)
            return x;
        }).toList());
        return m;
    }

    /** fix_1330: 정답 씰 없음 → 409 + code SEAL_NOT_REGISTERED */
    public static class SealNotRegisteredException extends IllegalStateException {
        public SealNotRegisteredException() { super("등록된 씰 정보가 없습니다"); }
    }
}
