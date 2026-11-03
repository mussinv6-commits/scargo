package com.scargo.ops;

import com.scargo.app.repository.TransportOrderRepository;
import com.scargo.entity.Truck;
import com.scargo.ops.VehicleUsePolicy.DriverInfo;
import com.scargo.ops.VehicleUsePolicy.GateEntry;
import com.scargo.ops.VehicleUsePolicy.TruckInfo;
import com.scargo.ops.controller.CarrierController;
import com.scargo.ops.entity.ShipmentLeg;
import com.scargo.ops.repository.ShipmentLegRepository;
import com.scargo.ops.service.BranchService;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.NotificationRepository;
import com.scargo.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 26.11.02 차량 진입 허가는 차량 심사, 기사 배정과 분리.
 *  게이트 입차 = S카고 등록 + APPROVED (hub_vehicles 는 레인 구분만) / 배차 = APPROVED 차량 + 유효 기사 (서버에서 확인)
 */
class VehicleUsePolicyTests {

    static TruckInfo truck(String no, long co, String category, String approval) { return new TruckInfo(no, co, category, approval); }
    static DriverInfo driver(long id, long co, String type, String approval) { return new DriverInfo(id, co, type, approval); }

    @Nested
    class GateEntryRules {
        @Test
        void approvedTruckWithoutDriverEnters() {
            // 진입 허가만 본다 (TruckInfo 에 기사 칸이 없다 = 기사 미배정이어도 입차 가능)
            GateEntry g = VehicleUsePolicy.gateEntry("부산90배7568", truck("부산90배7568", 1, "GENERAL_TRUCK", "APPROVED"), "COURIER");
            assertTrue(g.allowed());
            assertNull(g.blockReason());
            assertEquals("COURIER", g.kind());
        }

        @Test
        void pendingOrRejectedBlocked() {
            GateEntry p = VehicleUsePolicy.gateEntry("경남99바4892", truck("경남99바4892", 1, "CONTAINER_TRUCK", "PENDING"), "COURIER");
            assertEquals("BLOCK", p.decision());
            assertEquals("PENDING", p.blockReason());
            GateEntry r = VehicleUsePolicy.gateEntry("경남99바4892", truck("경남99바4892", 1, "CONTAINER_TRUCK", "REJECTED"), "COURIER");
            assertEquals("REJECTED", r.blockReason());
        }

        @Test
        void laneComesFromTrucksCategoryNotScannedLane() {
            // hub_vehicles 에 택배차로 있던 컨테이너 트럭(경남99바4892)도 trucks 기준 화물차, 출고 레인에서 찍혀도 마찬가지
            assertEquals("CARGO", VehicleUsePolicy.gateEntry("경남99바4892", truck("경남99바4892", 1, "CONTAINER_TRUCK", "APPROVED"), "COURIER").kind());
            assertEquals("COURIER", VehicleUsePolicy.gateEntry("울산81사5366", truck("울산81사5366", 1, "GENERAL_TRUCK", "APPROVED"), "CARGO").kind());
            GateEntry n = VehicleUsePolicy.gateEntry("서울1가1234", truck("서울1가1234", 1, null, "APPROVED"), "CARGO");
            assertEquals("CARGO", n.kind()); // 구분 미등록 → 스캔한 레인
        }

        @Test
        void unregisteredAndUnreadableBlocked() {
            GateEntry u = VehicleUsePolicy.gateEntry("부산91자4739", null, "CARGO"); // hub_vehicles 에만 있어도 S카고 미등록이면 차단
            assertEquals("BLOCK", u.decision());
            assertEquals("UNREGISTERED", u.blockReason());
            assertEquals("UNKNOWN", u.kind());
            assertEquals("UNREADABLE", VehicleUsePolicy.gateEntry(null, null, "CARGO").blockReason());
        }
    }

    @Nested
    class DispatchRules {
        @Test
        void approvedTruckNeedsDriver() {
            assertNotNull(VehicleUsePolicy.dispatchProblem(truck("V", 1, "GENERAL_TRUCK", "APPROVED"), null, 1L));
            assertNull(VehicleUsePolicy.dispatchProblem(truck("V", 1, "GENERAL_TRUCK", "APPROVED"), driver(9, 1, "BRANCH_DRIVER", "APPROVED"), 1L));
        }

        @Test
        void pendingOrRejectedTruckCannotBeDispatched() {
            DriverInfo d = driver(9, 1, "BRANCH_DRIVER", "APPROVED");
            assertTrue(VehicleUsePolicy.dispatchProblem(truck("V", 1, "GENERAL_TRUCK", "PENDING"), d, 1L).contains("심사대기"));
            assertTrue(VehicleUsePolicy.dispatchProblem(truck("V", 1, "GENERAL_TRUCK", "REJECTED"), d, 1L).contains("불허"));
            assertNotNull(VehicleUsePolicy.dispatchProblem(null, d, 1L));
        }

        @Test
        void driverMustBeApprovedSameCompanyAndMatchCategory() {
            TruckInfo t = truck("V", 1, "GENERAL_TRUCK", "APPROVED");
            assertNotNull(VehicleUsePolicy.dispatchProblem(t, driver(9, 1, "BRANCH_DRIVER", "PENDING"), 1L));
            assertNotNull(VehicleUsePolicy.dispatchProblem(t, driver(9, 2, "BRANCH_DRIVER", "APPROVED"), 1L));
            assertNotNull(VehicleUsePolicy.dispatchProblem(t, driver(9, 1, "CONTAINER_DRIVER", "APPROVED"), 1L));
            assertNotNull(VehicleUsePolicy.dispatchProblem(truck("C", 1, "CONTAINER_TRUCK", "APPROVED"), driver(9, 1, "BRANCH_DRIVER", "APPROVED"), 1L));
            assertNotNull(VehicleUsePolicy.dispatchProblem(t, driver(9, 1, "CORPORATE_APPROVED", "APPROVED"), 1L));
            assertNotNull(VehicleUsePolicy.dispatchProblem(truck("V", 2, "GENERAL_TRUCK", "APPROVED"), driver(9, 2, "BRANCH_DRIVER", "APPROVED"), 1L)); // 다른 운송사 차량
        }
    }

    // ── 서버 확인 (직접 API 호출 · 기사 변경) ──
    JdbcTemplate jdbc; VehicleUsePolicy policy;
    final Map<String, Map<String, Object>> trucks = new HashMap<>();
    final Map<Long, Map<String, Object>> accounts = new HashMap<>();

    void putTruck(String no, long co, String category, String approval) {
        trucks.put(no, new HashMap<>(Map.of("vehicle_no", no, "company_id", co, "vehicle_category", category, "entry_approval", approval)));
    }
    void putDriver(long id, long co, String type, String approval) {
        accounts.put(id, new HashMap<>(Map.of("company_id", co, "user_type", type, "approval_status", approval)));
    }

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenAnswer(inv -> {
            String sql = inv.getArgument(0); Object key = inv.getArgument(1);
            if (sql.contains("FROM trucks")) return trucks.containsKey(key) ? List.of(trucks.get(key)) : List.of();
            if (sql.contains("FROM accounts")) return accounts.containsKey(key) ? List.of(accounts.get(key)) : List.of();
            return List.of();
        });
        policy = new VehicleUsePolicy(jdbc);
        putTruck("서울80바1103", 1, "GENERAL_TRUCK", "APPROVED");
        putTruck("대구80자6850", 1, "GENERAL_TRUCK", "PENDING");
        putTruck("인천85바7777", 2, "GENERAL_TRUCK", "APPROVED");
        putDriver(10, 1, "BRANCH_DRIVER", "APPROVED");
        putDriver(20, 2, "BRANCH_DRIVER", "APPROVED");
    }

    @Test
    void policyNeverChangesApproval() {
        assertThrows(IllegalArgumentException.class, () -> policy.requireDispatchable("대구80자6850", 10L, 1L, "BRANCH_DRIVER"));
        policy.requireDispatchable("서울80바1103", 10L, 1L, "BRANCH_DRIVER");
        assertTrue(mockingDetails(jdbc).getInvocations().stream().noneMatch(i -> i.getMethod().getName().equals("update")
                || i.getMethod().getName().equals("execute") || i.getMethod().getName().equals("batchUpdate"))); // 읽기만 한다
        assertEquals("PENDING", trucks.get("대구80자6850").get("entry_approval"));
    }

    @Test
    void otherCompanyVehicleOrDriverIs403() {
        assertThrows(AccessDeniedException.class, () -> policy.requireDispatchable("인천85바7777", 20L, 1L, "BRANCH_DRIVER"));
        assertThrows(AccessDeniedException.class, () -> policy.requireDispatchable("서울80바1103", 20L, 1L, "BRANCH_DRIVER"));
    }

    @Nested
    class CarrierBranchApi {
        CarrierGuard guard; ShipmentLegRepository legs; TruckRepository truckRepo; CarrierController api;

        @BeforeEach
        void setUp() {
            guard = mock(CarrierGuard.class); legs = mock(ShipmentLegRepository.class); truckRepo = mock(TruckRepository.class);
            when(guard.myCompany()).thenReturn(1L);
            List<Truck> mine = new ArrayList<>();
            for (String no : List.of("서울80바1103", "대구80자6850")) { Truck t = new Truck(); t.setVehicleNo(no); mine.add(t); }
            when(truckRepo.findByCompany_CompanyId(1L)).thenReturn(mine);
            when(legs.save(any())).thenAnswer(inv -> { ShipmentLeg l = inv.getArgument(0); l.setAssignmentId(500L); return l; });
            api = new CarrierController(guard, mock(TransportOrderRepository.class), legs, truckRepo, mock(AccountRepository.class),
                    mock(NotificationRepository.class), mock(BranchService.class), policy);
        }

        Map<String, Object> body(String vehicle, Long driver) {
            Map<String, Object> b = new HashMap<>(Map.of("vehicleNo", vehicle, "fromCenterId", 1, "toCenterId", 2));
            if (driver != null) b.put("driverAccountId", driver);
            return b;
        }

        @Test
        void approvedVehicleWithoutDriverCannotBeDispatched() {
            assertThrows(IllegalArgumentException.class, () -> api.create(body("서울80바1103", null)));
            verify(legs, never()).save(any());
        }

        @Test
        void approvedVehicleWithValidDriverIsDispatched() {
            Map<String, Object> r = api.create(body("서울80바1103", 10L));
            assertEquals(10L, r.get("driverAccountId"));
            verify(legs).save(any());
        }

        @Test
        void pendingVehicleCannotBeDispatched() {
            assertThrows(IllegalArgumentException.class, () -> api.create(body("대구80자6850", 10L)));
            verify(legs, never()).save(any());
        }

        @Test
        void otherCompanyDriverRejected() {
            assertThrows(AccessDeniedException.class, () -> api.create(body("서울80바1103", 20L)));
            verify(legs, never()).save(any());
        }

        @Test
        void driverChangeRechecksVehicleApproval() {
            ShipmentLeg l = new ShipmentLeg();
            l.setAssignmentId(71L); l.setLegType(BranchService.LEG_TYPE); l.setVehicleNo("대구80자6850"); l.setStatus("ASSIGNED");
            when(legs.findById(71L)).thenReturn(Optional.of(l));
            assertThrows(IllegalArgumentException.class, () -> api.assign(71L, Map.of("driverAccountId", 10L)));
            assertNull(l.getDriverAccountId());

            putTruck("대구80자6850", 1, "GENERAL_TRUCK", "APPROVED"); // 허가 뒤에는 기사 변경 가능
            api.assign(71L, Map.of("driverAccountId", 10L));
            assertEquals(10L, l.getDriverAccountId());
        }
    }
}
