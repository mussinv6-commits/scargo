package com.scargo.ops.controller;

import com.scargo.Enum.NotificationType;
import com.scargo.app.entity.TransportOrder;
import com.scargo.app.repository.TransportOrderRepository;
import com.scargo.entity.Account;
import com.scargo.entity.Notification;
import com.scargo.entity.Truck;
import com.scargo.ops.CarrierGuard;
import com.scargo.ops.entity.ShipmentLeg;
import com.scargo.ops.repository.ShipmentLegRepository;
import com.scargo.ops.service.BranchService;
import com.scargo.ops.service.OpsNotifier;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.NotificationRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;

/** 26.10.20 W-07 운송오더 · W-08 지선 배차 · W-10 기사/차량 — 최소 구현. 승인 운송사 담당자 전용, 모두 company_id 로 거른다 (다른 운송사 = 403) */
@RestController
@RequestMapping("/api/web/carrier")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CARRIER')")
public class CarrierController {
    private final CarrierGuard guard;
    private final TransportOrderRepository orders;
    private final ShipmentLegRepository legs;
    private final TruckRepository trucks;
    private final AccountRepository accounts;
    private final NotificationRepository notifications;
    private final OpsNotifier notifier;
    private final BranchService branch;

    @GetMapping("/orders")
    public List<TransportOrder> orders() { return orders.findByCompanyIdOrderByOrderIdDesc(guard.myCompany()); }

    @GetMapping("/orders/{id}")
    public TransportOrder order(@PathVariable("id") Long id) {
        TransportOrder o = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("오더가 없습니다."));
        guard.requireOwn(o.getCompanyId());
        return o;
    }

    @GetMapping("/vehicles")
    public List<Map<String, Object>> vehicles() {
        return trucks.findByCompany_CompanyId(guard.myCompany()).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vehicleNo", t.getVehicleNo()); m.put("truckType", t.getTruckType()); m.put("status", t.getStatus());
            return m;
        }).toList();
    }

    @GetMapping("/drivers")
    public List<Map<String, Object>> drivers() {
        Long c = guard.myCompany();
        List<Map<String, Object>> r = new ArrayList<>();
        for (Account.UserType t : List.of(Account.UserType.CONTAINER_DRIVER, Account.UserType.BRANCH_DRIVER))
            for (Account a : accounts.findByCompanyIdAndUserType(c, t)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("accountId", a.getAccountId()); m.put("userName", a.getUserName()); m.put("userType", t.name()); m.put("phoneNum", a.getPhoneNum());
                r.add(m);
            }
        return r;
    }

    @GetMapping("/branch")
    public List<Map<String, Object>> branches() {
        Set<String> mine = myVehicles(guard.myCompany());
        if (mine.isEmpty()) return List.of();
        return legs.findByLegTypeAndVehicleNoInOrderByAssignmentIdDesc(BranchService.LEG_TYPE, mine).stream().map(this::legView).toList();
    }

    /** body: { vehicleNo, fromCenterId, toCenterId, plannedUnits, driverAccountId? } — 기사 없이 만들면 BRANCH_DRIVER_UNASSIGNED */
    @PostMapping("/branch")
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> b) {
        Long c = guard.myCompany();
        String vehicleNo = (String) b.get("vehicleNo");
        if (!myVehicles(c).contains(vehicleNo)) throw new AccessDeniedException("우리 운송사 차량이 아닙니다.");
        ShipmentLeg l = new ShipmentLeg();
        l.setLegType(BranchService.LEG_TYPE); l.setVehicleNo(vehicleNo);
        l.setFromCenterId(num(b.get("fromCenterId"))); l.setToCenterId(num(b.get("toCenterId")));
        if (l.getFromCenterId() == null || l.getToCenterId() == null) throw new IllegalArgumentException("출발 허브·도착 센터를 지정하세요.");
        Long pu = num(b.get("plannedUnits")); l.setPlannedUnits(pu == null ? null : pu.intValue());
        Long d = num(b.get("driverAccountId"));
        if (d != null) { requireMyDriver(c, d); l.setDriverAccountId(d); }
        l = legs.save(l);
        if (d == null) {
            String msg = "지선 운행 " + l.getAssignmentId() + " (" + vehicleNo + ") 기사 미배정";
            notifier.toCompany(c, NotificationType.BRANCH_DRIVER_UNASSIGNED, "[기사 미배정] 운행 " + l.getAssignmentId(), msg, l.getAssignmentId());
            notifier.toOps(l.getFromCenterId(), NotificationType.BRANCH_DRIVER_UNASSIGNED, "[기사 미배정] 운행 " + l.getAssignmentId(), msg, l.getAssignmentId());
        }
        return legView(l);
    }

    @PutMapping("/branch/{id}/driver")
    @Transactional
    public Map<String, Object> assign(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        Long c = guard.myCompany();
        ShipmentLeg l = legs.findById(id).filter(x -> BranchService.LEG_TYPE.equals(x.getLegType()))
                .orElseThrow(() -> new IllegalArgumentException("지선 운행이 없습니다."));
        if (!myVehicles(c).contains(l.getVehicleNo())) throw new AccessDeniedException("다른 운송사의 운행입니다.");
        if (!"ASSIGNED".equals(l.getStatus())) throw new IllegalStateException("출발 전 운행만 기사를 바꿀 수 있습니다.");
        Long d = num(b.get("driverAccountId"));
        requireMyDriver(c, d);
        l.setDriverAccountId(d);
        return legView(l);
    }

    /** 운송사 알림: company_id 대상만 (웹 목록 전용, 푸시 없음) */
    @GetMapping("/notifications")
    public List<Notification> notifications() { return notifications.findByCompanyIdOrderByCreatedAtDesc(guard.myCompany()); }

    private void requireMyDriver(Long c, Long d) {
        Account a = d == null ? null : accounts.findById(d).orElse(null);
        if (a == null || a.getUserType() != Account.UserType.BRANCH_DRIVER) throw new IllegalArgumentException("지선 운송기사 계정이 아닙니다.");
        if (!Objects.equals(a.getCompanyId(), c)) throw new AccessDeniedException("다른 운송사 기사입니다.");
    }

    private Set<String> myVehicles(Long c) {
        Set<String> s = new HashSet<>();
        for (Truck t : trucks.findByCompany_CompanyId(c)) s.add(t.getVehicleNo());
        return s;
    }

    private Map<String, Object> legView(ShipmentLeg l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tripId", l.getAssignmentId()); m.put("vehicleNo", l.getVehicleNo()); m.put("fromCenterId", l.getFromCenterId());
        m.put("toCenterId", l.getToCenterId()); m.put("driverAccountId", l.getDriverAccountId()); m.put("plannedUnits", l.getPlannedUnits());
        m.put("status", l.getStatus()); m.put("phase", branch.phase(l)); m.put("domesticSealNo", l.getDomesticSealNo());
        return m;
    }

    private static Long num(Object o) { return o == null ? null : o instanceof Number n ? n.longValue() : Long.valueOf(o.toString()); }
}
