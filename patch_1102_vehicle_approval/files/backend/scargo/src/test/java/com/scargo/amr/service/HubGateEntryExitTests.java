package com.scargo.amr.service;

import com.scargo.amr.dto.AmrDtos.VehicleScanRequest;
import com.scargo.amr.dto.AmrDtos.VehicleView;
import com.scargo.amr.entity.HubVehicle;
import com.scargo.amr.entity.HubVehicleLog;
import com.scargo.amr.repository.HubVehicleLogRepository;
import com.scargo.amr.repository.HubVehicleRepository;
import com.scargo.amr.security.AmrAccessPolicy;
import com.scargo.ops.VehicleUsePolicy;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 26.11.02 허브 게이트: 입차 = S카고 등록 + 진입 허가(trucks), 레인 = trucks.vehicle_category, 차단 사유 기록.
 *  출차는 예전 정책 그대로 (번호 일치면 EXIT_OK, 허가 상태와 무관 → 안에 있던 차가 새로 막히지 않음).
 *  OCR 서버(gate_api.py) 대신 이 테스트 안에서 /hub/scan 을 흉내 낸다.
 */
class HubGateEntryExitTests {
    HttpServer ocr; final AtomicReference<String> ocrPlate = new AtomicReference<>();
    JdbcTemplate jdbc; HubVehicleRepository hubVehicles; HubVehicleLogRepository logs; AmrService amr; HubVehicleService svc;
    final Map<String, Map<String, Object>> trucks = new HashMap<>();

    void putTruck(String no, String category, String approval) {
        Map<String, Object> m = new HashMap<>(); m.put("vehicle_no", no); m.put("company_id", 1L); m.put("vehicle_category", category); m.put("entry_approval", approval);
        trucks.put(no, m);
    }

    @BeforeEach
    void setUp() throws Exception {
        ocr = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ocr.createContext("/hub/scan", ex -> {
            String p = ocrPlate.get();
            byte[] body = ("{\"recognizedPlate\":" + (p == null ? "null" : "\"" + p + "\"") + ",\"photoPlate\":\"" + (p == null ? "사진" : p)
                    + "\",\"ocrConfidence\":0.97,\"engine\":\"test\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, body.length); ex.getResponseBody().write(body); ex.close();
        });
        ocr.start();

        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), (Object[]) any())).thenAnswer(inv -> {
            String sql = inv.getArgument(0); Object key = inv.getArgument(1);
            if (sql.contains("FROM trucks WHERE REPLACE(vehicle_no")) return trucks.containsKey(key) ? List.of(trucks.get(key)) : List.of();
            return List.of(); // 기준 씰 · 구간 관제 대기열 없음
        });
        hubVehicles = mock(HubVehicleRepository.class);
        logs = mock(HubVehicleLogRepository.class);
        when(logs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        amr = mock(AmrService.class);
        when(amr.hub(any(), any())).thenReturn(1L);
        svc = new HubVehicleService(amr, mock(AmrAccessPolicy.class), hubVehicles, logs, jdbc, new VehicleUsePolicy(jdbc),
                "http://127.0.0.1:" + ocr.getAddress().getPort(), "t");

        putTruck("서울80바1103", "GENERAL_TRUCK", "APPROVED");   // 허가 · 기사 미배정이어도 입차
        putTruck("006너9789", "CONTAINER_TRUCK", "APPROVED");    // hub_vehicles 에 없음
        putTruck("경남99바4892", "CONTAINER_TRUCK", "PENDING");  // hub_vehicles 에는 택배차로 있음
        putTruck("인천98바2578", "GENERAL_TRUCK", "REJECTED");
        when(hubVehicles.findById("경남99바4892")).thenReturn(Optional.of(new HubVehicle("경남99바4892", "COURIER", "S카고", "")));
        when(hubVehicles.findById("부산91자4739")).thenReturn(Optional.of(new HubVehicle("부산91자4739", "CARGO", "S카고", "")));
    }

    @AfterEach
    void tearDown() { ocr.stop(0); }

    VehicleView entry(String plate, String lane) {
        ocrPlate.set(plate);
        return svc.scan(1L, new VehicleScanRequest("ENTRY", lane, 0, null, null, null, null));
    }

    VehicleView exit(String expected, String recognized) {
        ocrPlate.set(recognized);
        return svc.scan(1L, new VehicleScanRequest("EXIT", "COURIER", 0, expected, 5, null, "서울"));
    }

    @Test
    void approvedVehicleEntersAndLaneFromTrucks() {
        VehicleView v = entry("서울80바1103", "CARGO");
        assertEquals("ALLOW", v.decision());
        assertEquals("COURIER", v.kind()); // 일반화물차 → 택배차 레인 (스캔 레인과 무관)
        assertNull(v.blockReason());
        VehicleView c = entry("006너9789", "COURIER"); // hub_vehicles 에 없어도 S카고 허가 차량이면 입차
        assertEquals("HOLD", c.decision());             // 화물차라 씰 대조 단계로 (기준 씰 없음 → 게이트 대기, 예전 규칙 그대로)
        assertEquals("CARGO", c.kind());
        assertEquals("MISSING", c.sealStatus());
    }

    @Test
    void pendingRejectedUnregisteredUnreadableBlockedWithReason() {
        VehicleView p = entry("경남99바4892", "COURIER");
        assertEquals("BLOCK", p.decision());
        assertEquals("PENDING", p.blockReason());
        assertEquals("CARGO", p.kind()); // hub_vehicles 의 COURIER 대신 trucks 기준
        assertEquals("REJECTED", entry("인천98바2578", "COURIER").blockReason());
        VehicleView u = entry("부산91자4739", "CARGO"); // hub_vehicles 에만 있음
        assertEquals("UNREGISTERED", u.blockReason());
        assertEquals("UNKNOWN", u.kind());
        VehicleView x = entry(null, "CARGO");
        assertEquals("BLOCK", x.decision());
        assertEquals("UNREADABLE", x.blockReason());
        verify(amr, atLeastOnce()).event(eq(1L), isNull(), eq("CRIT"), contains("진입 허가 심사대기 차단"));
    }

    @Test
    void exitPolicyUnchangedForUnapprovedVehicleInside() {
        VehicleView ok = exit("경남99바4892", "경남99바4892"); // 심사대기 차량이어도 출차는 번호 일치로만 판단
        assertEquals("EXIT_OK", ok.decision());
        assertNull(ok.blockReason());
        assertEquals(5, ok.boxCount());
        VehicleView bad = exit("경남99바4892", "서울80바1103");
        assertEquals("MISMATCH", bad.decision());
        assertNull(bad.blockReason());
        assertEquals("EXIT_OK", exit("부산91자4739", "부산91자4739").decision()); // S카고 미등록이어도 출차는 막지 않음
        assertThrows(ResponseStatusException.class, () -> svc.scan(1L, new VehicleScanRequest("EXIT", "COURIER", 0, null, null, null, null)));
        verify(logs, never()).save(argThat((HubVehicleLog l) -> "EXIT".equals(l.getDirection()) && "BLOCK".equals(l.getDecision())));
    }
}
