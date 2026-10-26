package com.scargo.ops.service;

import com.scargo.app.entity.Hub;
import com.scargo.app.repository.HubRepository;
import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.ops.entity.*;
import com.scargo.ops.repository.*;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** 26.10.20 허브 관제(W-04 조회) + 허브 앱(HUB_OP). 허브 = logistics_centers(center_type HUB) ↔ hubs.center_id */
@Service
@RequiredArgsConstructor
public class HubOpsService {
    private final HubRepository hubs;
    private final HubDockRepository docks;
    private final HubDispatchRepository dispatches;
    private final ShipmentLegRepository legs;
    private final ContainerSealRepository seals;
    private final SealEventRepository sealEvents;
    private final BranchUnitRepository units;
    private final LogisticsCenterRepository centers;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final ExceptionService exceptionService;
    private final BranchService branchService;
    private final OpsNotifier notifier;
    private final DockStatusService dockStatus; // v13: 도크 상태

    /** W-04. {id} = hubs.hub_id. 키 이름은 웹 목업(MOCK.hub)에 맞춤 */
    public Map<String, Object> monitor(Long hubId) {
        Hub hub = hubs.findById(hubId).orElseThrow(() -> new IllegalArgumentException("허브가 없습니다."));
        requireOwnHub(hub);
        Map<Long, String> dockNo = new HashMap<>();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docks", docks.findByHubIdOrderByDockCode(hubId).stream().map(d -> {
            dockNo.put(d.getDockId(), d.getDockCode());
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("dockId", d.getDockId()); x.put("no", d.getDockCode()); x.put("kind", d.getDockType());   // 11차 hub_docks.dock_type = IN | OUT x.put("status", d.getStatus());
            return x;
        }).toList());
        m.put("branches", legs.findByLegTypeAndFromCenterId(BranchService.LEG_TYPE, hub.getCenterId()).stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            long loaded = units.findByAssignmentId(l.getAssignmentId()).stream().filter(u -> !"REJECTED".equals(u.getStatus())).count();
            x.put("tripId", l.getAssignmentId()); x.put("destCenterId", l.getToCenterId()); x.put("status", branchService.phase(l));
            x.put("depart", l.getPlannedDepartAt()); x.put("units", List.of(loaded, l.getPlannedUnits() == null ? 0 : l.getPlannedUnits()));
            x.put("domesticSealNo", l.getDomesticSealNo()); x.put("hubArrivedAt", l.getHubArrivedAt());
            dispatches.findFirstByAssignmentId(l.getAssignmentId()).ifPresent(hd -> x.put("dispatch", Map.of("dispatchId", hd.getDispatchId(), "parcelCount", hd.getParcelCount(),
                    "totalWeightKg", hd.getTotalWeightKg(), "regionId", hd.getRegionId(), "departedAt", String.valueOf(hd.getDepartedAt()))));
            x.put("outDock", dispatches.findFirstByAssignmentId(l.getAssignmentId()).map(HubDispatch::getDockId).map(dockNo::get).orElse(null));
            return x;
        }).toList());
        // 26.10.20 씰 번호 앱→웹: 컨테이너 씰 현황(기대값·현장 확인값·보류)
        m.put("seals", seals.findAllByOrderBySealIdDesc().stream().map(this::sealBrief).toList());
        // TODO: inbound(도착 예정 컨테이너)·work(하역/스캔/분류)·ports·returns 는 transport_orders·hub_events·amr_* 연결 후 채운다
        return m;
    }

    /** W-08 △ : HUB_OP 출고 도크 연결 → hub_dispatches.dock_id (DB 설계 확정) */
    @Transactional
    public Map<String, Object> linkOutDock(Long tripId, Long dockId) {
        ShipmentLeg l = legs.findById(tripId).filter(x -> BranchService.LEG_TYPE.equals(x.getLegType()))
                .orElseThrow(() -> new IllegalArgumentException("지선 운행이 없습니다."));
        HubDock d = docks.findById(dockId).orElseThrow(() -> new IllegalArgumentException("도크가 없습니다."));
        Hub hub = hubs.findById(d.getHubId()).orElseThrow();
        requireOwnHub(hub);
        if (!Objects.equals(hub.getCenterId(), l.getFromCenterId())) throw new IllegalArgumentException("이 운행의 출발 허브 도크가 아닙니다.");
        if (!"OUT".equals(d.getDockType())) throw new IllegalArgumentException("출고 도크만 연결할 수 있습니다.");
        HubDispatch hd = dispatches.findFirstByAssignmentId(tripId).orElseGet(() -> {
            HubDispatch n = new HubDispatch();
            n.setAssignmentId(tripId); n.setHubId(hub.getHubId()); n.setDeliveryVehicleNo(l.getVehicleNo());
            // 26.10.20 확정: region_id = 지선 목적지 센터의 region_id
            n.setRegionId(centers.findById(l.getToCenterId()).map(LogisticsCenter::getRegionId)
                    .orElseThrow(() -> new IllegalStateException("목적지 센터의 region_id 가 없습니다.")));
            n.setDepartedAt(null); // 26.10.20 확정: 07 SQL 에서 departed_at DROP DEFAULT. 출발 확정 때 채운다
            return n;
        });
        Long oldDock = hd.getDockId();
        hd.setDockId(dockId);
        dispatches.save(hd);
        // v13: 새 도크 DOCKED, 다른 도크에서 옮겨 왔으면 예전 도크는 남은 운행 기준으로 다시 계산(EMPTY 등)
        String dockSt = dockStatus.refresh(dockId);
        if (oldDock != null && !oldDock.equals(dockId)) dockStatus.refresh(oldDock);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tripId", tripId); r.put("dispatchId", hd.getDispatchId()); r.put("dockId", dockId); r.put("dockNo", d.getDockCode()); r.put("dockStatus", dockSt);
        return r;
    }

    public List<HubDock> myDocks() { return docks.findByHubIdOrderByDockCode(myHub().getHubId()); }

    public Map<String, Object> seal(Long id) {
        ContainerSeal s = seals.findById(id).orElseThrow(() -> new IllegalArgumentException("씰이 없습니다."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seal", s);
        // 허브 인수 여부 = 소속 허브에서 INSPECTED 이벤트가 있는지 (container_seals.status 는 ACTIVE 등 11차 값 그대로)
        Long hubCenter = me().getScopeCenterId();
        m.put("receipt", sealEvents.findBySealIdOrderByOccurredAtDesc(id).stream()
                .anyMatch(e -> "INSPECTED".equals(e.getEventType()) && Objects.equals(e.getCenterId(), hubCenter)) ? "SEAL_RECEIVED" : "SEAL_PENDING");
        return m;
    }

    /** 허브 씰 확인: seal_events(INSPECTED) 기록 → 불일치/훼손/MISSING 이면 예외 + open_hold = true + SEAL_OPEN_HOLD 알림
     *  26.10.20 최종: 검사 중 MISSING 은 SEAL_LOST 예외만 만들고 container_seals 행(status)은 바꾸지 않는다. 분실 확정은 reportLost() */
    @Transactional
    public Map<String, Object> receive(Long sealId, String observedSealNo, String condition) {
        ContainerSeal s = seals.findById(sealId).orElseThrow(() -> new IllegalArgumentException("씰이 없습니다."));
        Account me = me();
        String cond = condition == null ? "INTACT" : condition;
        if (!Set.of("INTACT", "DAMAGED", "MISSING").contains(cond)) throw new IllegalArgumentException("observed_condition 값이 아닙니다.");
        SealEvent ev = newEvent(s, me, "INSPECTED", cond, "MISSING".equals(cond) ? null : observedSealNo);   // CHECK: MISSING 이면 observed NULL
        OpsException x = exceptionService.fromSealEvent(ev);
        if (x != null) {
            s.setOpenHold(true);    // 보류는 자동 해제하지 않는다
            notifier.toOps(me.getScopeCenterId(), com.scargo.Enum.NotificationType.SEAL_OPEN_HOLD, "[개봉 보류] " + s.getContainerNo(),
                    x.getMessage(), x.getExceptionId());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seal", s); m.put("eventId", ev.getEventId()); m.put("exceptionId", x == null ? null : x.getExceptionId());
        return m;
    }

    /** 분실 확정: seal_events(LOST) + 같은 트랜잭션에서 container_seals ACTIVE → LOST, removed_at = occurred_at */
    @Transactional
    public Map<String, Object> reportLost(Long sealId, String reason) {
        ContainerSeal s = seals.findById(sealId).orElseThrow(() -> new IllegalArgumentException("씰이 없습니다."));
        if (!"ACTIVE".equals(s.getStatus())) throw new IllegalStateException("활성(ACTIVE) 씰만 분실 처리할 수 있습니다.");
        Account me = me();
        SealEvent ev = newEvent(s, me, "LOST", "MISSING", null, reason);
        s.setStatus("LOST"); s.setRemovedAt(ev.getOccurredAt());
        OpsException x = exceptionService.fromSealEvent(ev);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seal", s); m.put("eventId", ev.getEventId()); m.put("exceptionId", x == null ? null : x.getExceptionId());
        return m;
    }

    private SealEvent newEvent(ContainerSeal s, Account me, String type, String cond, String observed) { return newEvent(s, me, type, cond, observed, null); }

    private SealEvent newEvent(ContainerSeal s, Account me, String type, String cond, String observed, String reason) {
        SealEvent ev = new SealEvent();
        ev.setSealId(s.getSealId()); ev.setShipmentId(s.getShipmentId()); ev.setExpectedSealNo(s.getSealNo());
        ev.setEventType(type); ev.setObservedCondition(cond); ev.setObservedSealNo(observed); ev.setReason(reason);
        ev.setActorAccountId(me.getAccountId()); ev.setActorName(me.getUserName()); ev.setCenterId(me.getScopeCenterId());
        ev.setOccurredAt(OffsetDateTime.now());
        return sealEvents.save(ev);
    }

    /** 씰 불일치 개봉 승인 — 2차는 OTP 없음, 이 동작만 비밀번호 재인증 (서버 검증). seal_events(OPENED) 기록 */
    @Transactional
    public ContainerSeal approveOpen(Long sealId, String password) {
        Account me = me();
        if (password == null || !passwordEncoder.matches(password, me.getUserPw()))
            throw new IllegalArgumentException("비밀번호가 맞지 않습니다. 개봉 승인은 재인증이 필요합니다.");
        ContainerSeal s = seals.findById(sealId).orElseThrow(() -> new IllegalArgumentException("씰이 없습니다."));
        if (!Boolean.TRUE.equals(s.getOpenHold())) throw new IllegalStateException("개봉 보류 상태가 아닙니다.");
        s.setOpenHold(false);
        SealEvent ev = new SealEvent();
        ev.setSealId(sealId); ev.setShipmentId(s.getShipmentId()); ev.setExpectedSealNo(s.getSealNo());
        ev.setEventType("OPENED"); ev.setObservedCondition("UNKNOWN"); ev.setReason("개봉 보류 해제 (허브 관제사 재인증 승인)");
        ev.setActorAccountId(me.getAccountId()); ev.setActorName(me.getUserName()); ev.setCenterId(me.getScopeCenterId());
        sealEvents.save(ev);
        sealEvents.findBySealIdOrderByOccurredAtDesc(sealId)
                .forEach(e -> exceptionService.resolveBySource("seal_events", String.valueOf(e.getEventId()), "허브 관제사 " + me.getUserName() + " 개봉 승인(재인증)"));
        return s;
    }

    /** 씰 보류 판단 (HUB_OP).
     *  RELEASE: 비밀번호 재인증 → open_hold=false, seal_events OPENED, 관련 예외 RESOLVED (= approveOpen)
     *  KEEP   : open_hold 유지, reclass_required 지정, 관련 예외 OPEN → IN_PROGRESS(담당 = 나) */
    @Transactional
    public Map<String, Object> holdDecision(Long sealId, String action, String password, Boolean reclassRequired) {
        ContainerSeal s;
        if ("RELEASE".equals(action)) {
            s = approveOpen(sealId, password);
            if (reclassRequired != null) s.setReclassRequired(reclassRequired);
        } else if ("KEEP".equals(action)) {
            s = seals.findById(sealId).orElseThrow(() -> new IllegalArgumentException("씰이 없습니다."));
            if (!Boolean.TRUE.equals(s.getOpenHold())) throw new IllegalStateException("개봉 보류 상태가 아닙니다.");
            if (reclassRequired != null) s.setReclassRequired(reclassRequired);
            Account me = me();
            sealEvents.findBySealIdOrderByOccurredAtDesc(sealId).forEach(e -> exceptionService.markInProgress("seal_events", String.valueOf(e.getEventId()), me.getAccountId()));
        } else throw new IllegalArgumentException("action 은 RELEASE 또는 KEEP 입니다.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sealId", s.getSealId()); m.put("openHold", s.getOpenHold()); m.put("reclassRequired", s.getReclassRequired()); m.put("status", s.getStatus());
        return m;
    }

    /** 재분류 완료 체크: 롤테이너를 다시 분류 대기로 */
    @Transactional
    public Map<String, Object> reclassDone(String unitId) {
        BranchUnit u = units.findById(unitId).orElseThrow(() -> new IllegalArgumentException("롤테이너가 없습니다."));
        u.setStatus("SORTING"); u.setAssignmentId(null);
        return branchService.unitView(u);
    }

    private Map<String, Object> sealBrief(ContainerSeal s) {
        Map<String, Object> x = new LinkedHashMap<>();
        SealEvent last = sealEvents.findBySealIdOrderByOccurredAtDesc(s.getSealId()).stream().filter(e -> !"OPENED".equals(e.getEventType())).findFirst().orElse(null);
        x.put("sealId", s.getSealId()); x.put("containerNo", s.getContainerNo()); x.put("expected", s.getSealNo());
        x.put("read", last == null ? null : last.getObservedSealNo()); x.put("matches", last == null ? null : ExceptionService.matches(last));
        x.put("status", s.getStatus()); x.put("openHold", s.getOpenHold()); x.put("at", last == null ? null : last.getOccurredAt());
        return x;
    }

    private Account me() { return accounts.findById(CurrentAccount.id()).orElseThrow(); }

    private Hub myHub() {
        Long hubId = me().getHubId();
        if (hubId == null) throw new IllegalStateException("소속 허브(accounts.hub_id)가 없습니다.");
        return hubs.findById(hubId).orElseThrow(() -> new IllegalStateException("없는 허브입니다: " + hubId));
    }

    private void requireOwnHub(Hub hub) {
        Account me = me();
        if (me.getUserType() == Account.UserType.HUB_OP && !Objects.equals(me.getHubId(), hub.getHubId()))
            throw new IllegalStateException("소속 허브만 볼 수 있습니다.");
    }
}
