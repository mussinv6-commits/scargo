package com.scargo.ops.service;

import com.scargo.app.security.CurrentAccount;
import com.scargo.ops.entity.*;
import com.scargo.ops.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/** 26.10.20 지선 운행 = shipment_legs (leg_type HUB_TO_CENTER). 지선 운송기사 앱 P-01 ~ P-06.
 *  shipment_legs.status 는 ASSIGNED/IN_TRANSIT/ARRIVED/RECEIVED(fix_1440 인수 확정)/CANCELED 를 저장하고, 화면 단계는 phase() 로 계산:
 *  ASSIGNED → LOADING(일부 상차) → LOADED(예정 수 = 상차 수) → IN_TRANSIT → ARRIVED → UNLOADED(전 물품 unloaded_at)
 *  → INSPECTED(inspected_at) → RECEIVED(received_at) */
@Service
@RequiredArgsConstructor
public class BranchService {
    public static final String LEG_TYPE = "HUB_TO_CENTER";
    private final ShipmentLegRepository legs;
    private final BranchUnitRepository units;
    private final ParcelRepository parcels;
    private final ItemRepository items;
    private final ItemVehicleAssignmentRepository iva;
    private final ExceptionService exceptionService;
    private final HubDispatchRepository dispatches;
    private final DockStatusService dockStatus; // v13: 출발 → LEAVING, 도착 → EMPTY
    private final OpsNotifier notifier;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc; // fix_1330: 도착 봉인 입력 이력

    public ShipmentLeg mine(Long id) {
        ShipmentLeg l = legs.findById(id).filter(x -> LEG_TYPE.equals(x.getLegType()))
                .orElseThrow(() -> new IllegalArgumentException("지선 운행이 없습니다."));
        if (!Objects.equals(l.getDriverAccountId(), CurrentAccount.id())) throw new IllegalStateException("내 운행이 아닙니다.");
        return l;
    }

    public String phase(ShipmentLeg l) {
        if (l.getReceivedAt() != null) return "RECEIVED";
        if (l.getInspectedAt() != null) return "INSPECTED";
        if ("ARRIVED".equals(l.getStatus())) {
            List<ItemVehicleAssignment> a = iva.findByAssignmentId(l.getAssignmentId());
            if (!a.isEmpty()) return a.stream().allMatch(x -> x.getUnloadedAt() != null) ? "UNLOADED" : "ARRIVED";
            // fix_1420: 물품 배정이 없는 운행(롤테이너만 있는 시드 등)은 롤테이너 하차 상태로 판정
            List<BranchUnit> us = units.findByAssignmentId(l.getAssignmentId());
            return !us.isEmpty() && us.stream().allMatch(u -> "UNLOADED".equals(u.getStatus())) ? "UNLOADED" : "ARRIVED";
        }
        if (!"ASSIGNED".equals(l.getStatus())) return l.getStatus();
        long loaded = loadedUnits(l.getAssignmentId());
        if (loaded == 0) return l.getHubArrivedAt() != null && l.getDepartedAt() == null ? "AT_HUB" : "ASSIGNED";
        return l.getPlannedUnits() != null && loaded == l.getPlannedUnits() ? "LOADED" : "LOADING";
    }

    private long loadedUnits(Long tripId) { return units.findByAssignmentId(tripId).stream().filter(u -> "LOADED".equals(u.getStatus()) || "UNLOADED".equals(u.getStatus())).count(); }

    /** 파손 수 = items.status 'DAMAGED' 집계 (컬럼 없음) */
    public long damagedCount(Long tripId) {
        List<Long> ids = iva.findByAssignmentId(tripId).stream().map(ItemVehicleAssignment::getItemId).toList();
        return ids.isEmpty() ? 0 : items.countByItemIdInAndStatus(ids, "DAMAGED");
    }

    public Map<String, Object> view(ShipmentLeg l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("trip", l); m.put("phase", phase(l)); m.put("damaged", damagedCount(l.getAssignmentId()));
        m.put("units", units.findByAssignmentId(l.getAssignmentId()).stream().map(this::unitView).toList());
        return m;
    }

    public Map<String, Object> unitView(BranchUnit u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitId", u.getUnitId()); m.put("destCenterId", u.getDestCenterId()); m.put("status", u.getStatus());
        m.put("boxCount", parcels.countByUnitId(u.getUnitId()));   // parcels.unit_id 집계
        return m;
    }

    public Map<String, Object> current() {
        ShipmentLeg cur = legs.findByLegTypeAndDriverAccountIdOrderByAssignmentIdDesc(LEG_TYPE, CurrentAccount.id()).stream()
                .filter(l -> l.getReceivedAt() == null && !"CANCELED".equals(l.getStatus())).findFirst().orElse(null);
        return cur == null ? Map.of("trip", Map.of()) : view(cur);
    }

    /** 허브 도착 (P-02): shipment_legs.hub_arrived_at. status 는 ASSIGNED 그대로, 화면 단계만 AT_HUB */
    @Transactional
    public Map<String, Object> hubArrive(Long tripId) {
        ShipmentLeg t = mine(tripId);
        if (!"ASSIGNED".equals(t.getStatus())) throw new IllegalStateException("출발 전 운행만 허브 도착 처리할 수 있습니다.");
        if (t.getHubArrivedAt() == null) t.setHubArrivedAt(OffsetDateTime.now());
        dockStatus.refreshForTrip(tripId); // v13: 도크가 연결돼 있으면 DOCKED (이미 DOCKED 면 그대로)
        return view(t);
    }

    /** 상차 스캔: ① 박스(parcels→items.destination_center_id) = 롤테이너 목적지 ② 롤테이너 목적지 = 운행 목적지 */
    @Transactional
    public Map<String, Object> scanLoad(Long tripId, String unitId) {
        ShipmentLeg t = mine(tripId);
        if (!"ASSIGNED".equals(t.getStatus())) throw new IllegalStateException("출발 전에만 상차할 수 있습니다.");
        BranchUnit u = units.findById(unitId).orElseThrow(() -> new IllegalArgumentException("롤테이너가 없습니다: " + unitId));
        List<Parcel> ps = parcels.findByUnitId(unitId);
        List<Parcel> wrong = ps.stream().filter(p -> items.findById(p.getItemId()).map(i -> !Objects.equals(i.getDestinationCenterId(), u.getDestCenterId())).orElse(true)).toList();
        boolean boxOk = wrong.isEmpty();
        boolean destOk = Objects.equals(u.getDestCenterId(), t.getToCenterId());
        if (boxOk && destOk) {
            OffsetDateTime now = OffsetDateTime.now();
            u.setAssignmentId(tripId); u.setStatus("LOADED"); u.setLoadedAt(now);
            for (Parcel p : ps) {
                Item i = items.findById(p.getItemId()).orElseThrow();
                ItemVehicleAssignment a = iva.findByItemIdAndAssignmentId(i.getItemId(), tripId).orElseGet(ItemVehicleAssignment::new);
                a.setItemId(i.getItemId()); a.setShipmentId(i.getShipmentId()); a.setAssignmentId(tripId);
                a.setLoadedAt(now); a.setStatus("LOADED"); iva.save(a);
                i.setStatus("LOADED");
            }
        } else {
            u.setStatus("REJECTED");
            // SORT_FAILED: 박스가 잘못 실렸으면 그 박스(parcels → item) 기준, 롤테이너 자체가 다른 센터행이면 원본 없음
            // 26.10.20 최종: 잘못 담긴 박스 → parcels/parcel_no, 다른 센터행 롤테이너 → branch_units/unit_id + assignment_id
            if (!boxOk) for (Parcel p : wrong) {
                p.setStatus("SORT_FAILED");
                exceptionService.raise("SORT_FAILED", "CRITICAL", t.getFromCenterId(), tripId, "parcels", p.getParcelNo(),
                        "박스 ≠ 롤테이너 목적지 · " + p.getParcelNo() + " in " + unitId, null);
            } else exceptionService.raise("SORT_FAILED", "CRITICAL", t.getFromCenterId(), tripId, "branch_units", unitId,
                        "롤테이너 ≠ 운행 목적지 · " + unitId + " → 운행 " + tripId, null);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("unitId", unitId); r.put("boxDestOk", boxOk); r.put("tripDestOk", destOk); r.put("status", u.getStatus()); r.put("phase", phase(t));
        return r;
    }

    /** ③ 예정 롤테이너 수 = 실린 수 */
    @Transactional(readOnly = true)
    public Map<String, Object> confirmLoad(Long tripId) {
        ShipmentLeg t = mine(tripId);
        long loaded = loadedUnits(tripId);
        if (t.getPlannedUnits() != null && loaded != t.getPlannedUnits())
            throw new IllegalStateException("상차 수량 불일치: " + loaded + "/" + t.getPlannedUnits());
        return view(t);
    }

    @Transactional
    public Map<String, Object> depart(Long tripId, String sealNo) {
        if (sealNo == null || sealNo.isBlank()) throw new IllegalArgumentException("씰번호를 입력하세요.");
        ShipmentLeg t = mine(tripId);
        if (!"LOADED".equals(phase(t))) throw new IllegalStateException("상차 확인이 끝나야 출발할 수 있습니다 (지금: " + phase(t) + ")");
        OffsetDateTime now = OffsetDateTime.now();
        t.setDomesticSealNo(sealNo.trim()); t.setStatus("IN_TRANSIT"); t.setDepartedAt(now);
        iva.findByAssignmentId(tripId).forEach(a -> items.findById(a.getItemId()).ifPresent(i -> i.setStatus("IN_TRANSIT")));
        // 26.10.20 최종: 출고 확정 = hub_dispatches 집계 (parcels.dispatch_id → parcel_count·total_weight_kg, departed_at)
        HubDispatch hd = dispatches.findFirstByAssignmentId(tripId).orElse(null);
        if (hd == null) {
            String msg = "출고 도크 미연결 상태로 출발 · 운행 " + tripId + " (" + t.getVehicleNo() + ")";
            notifier.toOps(t.getFromCenterId(), com.scargo.Enum.NotificationType.BRANCH_DOCK_UNLINKED, "[도크 미연결] 운행 " + tripId, msg, tripId);
            notifier.toCompany(notifier.companyOfVehicle(t.getVehicleNo()), com.scargo.Enum.NotificationType.BRANCH_DOCK_UNLINKED, "[도크 미연결] 운행 " + tripId, msg, tripId);
        } else {
            java.math.BigDecimal w = java.math.BigDecimal.ZERO; int n = 0;
            for (BranchUnit u : units.findByAssignmentId(tripId)) {
                if (!"LOADED".equals(u.getStatus())) continue;
                for (Parcel p : parcels.findByUnitId(u.getUnitId())) {
                    p.setDispatchId(hd.getDispatchId()); n++;
                    w = w.add(items.findById(p.getItemId()).map(Item::getWeightKg).orElse(java.math.BigDecimal.ZERO));
                }
            }
            hd.setParcelCount(n); hd.setTotalWeightKg(w.setScale(1, java.math.RoundingMode.HALF_UP)); hd.setDepartedAt(now);
            dockStatus.refresh(hd.getDockId()); // v13: 출발 → LEAVING (같은 도크에 출발 전 운행이 있으면 DOCKED 유지)
        }
        return view(t);
    }

    @Transactional public Map<String, Object> arrive(Long id) {
        ShipmentLeg t = need(id, "IN_TRANSIT"); t.setStatus("ARRIVED"); t.setArrivedAt(OffsetDateTime.now());
        dockStatus.refreshForTrip(id); // v13: 운행이 도크를 완전히 떠남 → EMPTY (다른 운행이 있으면 그 기준)
        return view(t);
    }

    /** 하차 완료: item_vehicle_assignments.unloaded_at + 롤테이너 UNLOADED */
    @Transactional public Map<String, Object> unloaded(Long id) {
        ShipmentLeg t = need(id, "ARRIVED");
        OffsetDateTime now = OffsetDateTime.now();
        iva.findByAssignmentId(id).forEach(a -> { a.setUnloadedAt(now); a.setStatus("UNLOADED"); });
        units.findByAssignmentId(id).forEach(u -> { u.setStatus("UNLOADED"); u.setUnloadedAt(now); });
        return view(t);
    }

    /** 검수 완료: 파손 물품은 items.status = 'DAMAGED' + ITEM_DAMAGED 예외 */
    @Transactional public Map<String, Object> inspected(Long id, List<Long> damagedItemIds) {
        ShipmentLeg t = mine(id);
        if (t.getReceivedAt() != null) throw new IllegalStateException("이미 인수 확정된 운행입니다."); // fix_1450
        if (!"UNLOADED".equals(phase(t))) throw new IllegalStateException("하차가 끝나야 검수할 수 있습니다.");
        Set<Long> onTrip = new HashSet<>(iva.findByAssignmentId(id).stream().map(ItemVehicleAssignment::getItemId).toList());
        for (Long itemId : damagedItemIds == null ? List.<Long>of() : damagedItemIds) {
            if (!onTrip.contains(itemId)) throw new IllegalArgumentException("이 운행의 물품이 아닙니다: " + itemId);
            items.findById(itemId).ifPresent(i -> i.setStatus("DAMAGED"));
            exceptionService.raise("ITEM_DAMAGED", "WARNING", t.getToCenterId(), id, "items", String.valueOf(itemId), "센터 검수 파손 · item " + itemId + " (운행 " + id + ")", null);
        }
        t.setInspectedAt(OffsetDateTime.now());
        return view(t);
    }

    /** fix_1300: 지선 국내 봉인 대조. 센터가 도착한 차량의 봉인 번호를 확인해 입력 → 허브 출발 때 기사가 입력한 domestic_seal_no 와 비교.
     *  다르면 SEAL_MISMATCH(CRITICAL, 도착 센터, 운행 연결) 예외 + 관제·센터·차량 운송사 알림. 같은 운행은 마지막 확인값으로 덮어쓴다 */
    @Transactional public Map<String, Object> confirmArrivalSeal(Long id, String sealNo, com.scargo.entity.Account actor) {
        if (sealNo == null || sealNo.isBlank()) throw new IllegalArgumentException("도착 봉인 번호를 입력하세요.");
        ShipmentLeg t = legs.findById(id).orElseThrow(() -> new IllegalArgumentException("운행이 없습니다."));
        if (!LEG_TYPE.equals(t.getLegType())) throw new IllegalArgumentException("지선 운행이 아닙니다.");
        if (actor.getUserType() == com.scargo.entity.Account.UserType.CENTER_OP && !java.util.Objects.equals(actor.getCenterId(), t.getToCenterId()))
            throw new org.springframework.security.access.AccessDeniedException("소속 센터로 오는 운행만 확인할 수 있습니다.");
        if (t.getDepartedAt() == null) throw new IllegalStateException("아직 허브에서 출발하지 않은 운행입니다.");
        String in = sealNo.trim().toUpperCase();
        t.setArrivalSealNo(in); t.setArrivalSealCheckedAt(OffsetDateTime.now()); t.setArrivalSealCheckedBy(actor.getUserName());
        boolean match = in.equals(t.getDomesticSealNo());
        Long xid = null;
        if (!match) {
            String msg = "지선 봉인 불일치 · 운행 LH#" + id + " (" + t.getVehicleNo() + ") · 허브 출발 " + (t.getDomesticSealNo() == null ? "미입력" : t.getDomesticSealNo()) + " / 센터 도착 " + in;
            // fix_1330: 같은 운행의 열린 불일치 예외는 1건만. 이미 있으면 새 예외·중복 알림 없이 입력 이력만 추가하고 메시지를 최근 값으로 갱신
            List<Long> open = jdbc.queryForList("SELECT exception_id FROM exceptions WHERE exception_type = 'SEAL_MISMATCH' AND assignment_id = ? AND source_table IS NULL AND status <> 'RESOLVED' ORDER BY exception_id LIMIT 1", Long.class, id);
            if (!open.isEmpty()) {
                xid = open.get(0);
                jdbc.update("UPDATE exceptions SET message = ? WHERE exception_id = ?", msg, xid);
            } else {
                OpsException x = exceptionService.raise("SEAL_MISMATCH", "CRITICAL", t.getToCenterId(), id, null, null, msg, null);
                xid = x == null ? null : x.getExceptionId();
                notifier.toCompany(notifier.companyOfVehicle(t.getVehicleNo()), com.scargo.Enum.NotificationType.SEAL_OPEN_HOLD, "[지선 봉인 불일치] LH#" + id, msg, xid);
            }
        }
        jdbc.update("INSERT INTO branch_seal_checks (assignment_id, observed_seal_no, expected_seal_no, matches, actor_account_id, actor_name, exception_id) VALUES (?,?,?,?,?,?,?)",
                id, in, t.getDomesticSealNo(), match, actor.getAccountId(), actor.getUserName(), xid);
        Map<String, Object> r = new LinkedHashMap<>(view(t));
        r.put("arrivalMatches", match); r.put("exceptionId", xid);
        r.put("history", jdbc.queryForList("SELECT observed_seal_no AS \"sealNo\", matches, actor_name AS \"by\", checked_at AS \"at\" FROM branch_seal_checks WHERE assignment_id = ? ORDER BY check_id DESC", id));
        return r;
    }

    @Transactional public Map<String, Object> receive(Long id) {
        ShipmentLeg t = mine(id);
        if (t.getReceivedAt() != null) throw new IllegalStateException("이미 인수 확정된 운행입니다."); // fix_1440
        if (!"INSPECTED".equals(phase(t))) throw new IllegalStateException("검수가 끝나야 인수 확정할 수 있습니다.");
        t.setReceivedAt(OffsetDateTime.now());
        t.setStatus("RECEIVED"); // fix_1440: 센터 인수 확정 = 운행 종료 상태
        iva.findByAssignmentId(id).forEach(a -> items.findById(a.getItemId()).filter(i -> !"DAMAGED".equals(i.getStatus())).ifPresent(i -> i.setStatus("DELIVERED")));
        return view(t);
    }

    private ShipmentLeg need(Long id, String status) {
        ShipmentLeg t = mine(id);
        if (!status.equals(t.getStatus())) throw new IllegalStateException("현재 상태(" + phase(t) + ")에서는 할 수 없습니다.");
        return t;
    }
}
