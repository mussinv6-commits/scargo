package com.scargo.amr.service;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.entity.HubVehicle;
import com.scargo.amr.entity.HubVehicleLog;
import com.scargo.amr.repository.HubVehicleLogRepository;
import com.scargo.amr.repository.HubVehicleRepository;
import com.scargo.amr.security.AmrAccessPolicy;
import com.scargo.amr.security.AmrAccessPolicy.Access;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;

// 26.10.15 허브 입출차 OCR
//  1) 웹 관제(현장 역할)가 트럭이 입구 · 출구에 오면 POST /api/amr/vehicles/scan
//  2) 여기서 1차 OCR 서버(gate_api.py :8001 /hub/scan, YOLO + CRNN)를 호출해 번호판을 읽고
//  3) hub_vehicles(등록 차량)로 차종 판정: 화물차 → 입고 도크 / 택배차 → 출고 도크 / 미등록 → 차단
//  4) hub_vehicle_logs 에 기록 → state 로 앱 · 웹이 2초 안에 같이 본다
// 26.10.22 허브별 권한: 스캔 · 씰 확인은 MANAGE, 기록 · 사진 조회는 VIEW (AmrAccessPolicy)
@Service
public class HubVehicleService implements ApplicationRunner {

    private final AmrService amr;
    private final AmrAccessPolicy access;
    private final HubVehicleRepository vehicleRepo;
    private final HubVehicleLogRepository logRepo;
    private final String ocrBase;
    private final String ocrToken; // gate_api.py 의 X-Gate-Token (환경변수 GATE_API_TOKEN 과 같은 값)
    private final RestTemplate rest;
    private final JdbcTemplate jdbc;

    public HubVehicleService(AmrService amr, AmrAccessPolicy access, HubVehicleRepository vehicleRepo, HubVehicleLogRepository logRepo, JdbcTemplate jdbc,
                             @Value("${amr.ocr.base:http://localhost:8001}") String ocrBase,
                             @Value("${amr.ocr.token:${GATE_API_TOKEN:}}") String ocrToken) {
        this.amr = amr; this.access = access; this.vehicleRepo = vehicleRepo; this.logRepo = logRepo; this.jdbc = jdbc; this.ocrBase = ocrBase; this.ocrToken = ocrToken;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(20000); // CPU 인식은 첫 호출에 몇 초 걸릴 수 있음
        this.rest = new RestTemplate(f);
    }

    // 26.10.20 기준 씰: 팀 DB(11차) container_seals(ACTIVE) 를 차량번호로 조회
    //  transport_orders.vehicle_no → (shipment_id, container_no) → container_seals. 없으면 null → 게이트 대기
    private Map<String, Object> sealRow(String plate) {
        try {
            List<Map<String, Object>> r = jdbc.queryForList("SELECT cs.seal_id, cs.shipment_id, cs.seal_no, t.order_id FROM transport_orders t "
                    + "JOIN container_seals cs ON cs.shipment_id = t.shipment_id AND cs.container_no = t.container_no "
                    + "WHERE t.vehicle_no = ? AND cs.status = 'ACTIVE' ORDER BY cs.seal_id DESC LIMIT 1", plate);
            return r.isEmpty() ? null : r.get(0);
        } catch (Exception e) { return null; }
    }

    private String refSeal(String plate) {
        Map<String, Object> r = sealRow(plate);
        return r == null ? null : str(r.get("seal_no"));
    }

    // 도크 확인 결과를 팀 DB seal_events(INSPECTED)에 남긴다 (번호 일치 여부는 DB가 number_matches 로 자동 계산). 실패해도 흐름은 계속
    private void sealEvent(String plate, String observed) {
        try {
            Map<String, Object> r = sealRow(plate);
            if (r == null) return;
            jdbc.update("INSERT INTO seal_events (seal_id, shipment_id, order_id, event_type, expected_seal_no, observed_seal_no, observed_condition, actor_name) "
                    + "VALUES (?, ?, ?, 'INSPECTED', ?, ?, 'UNKNOWN', '허브 도크')", r.get("seal_id"), r.get("shipment_id"), r.get("order_id"), r.get("seal_no"), observed);
        } catch (Exception ignored) { }
    }

    // ── 등록 차량 기본값 (비어 있을 때 한 번만) · 사진 폴더 python/허브_수신함 과 같은 번호 ──
    @Override
    public void run(ApplicationArguments args) {
        try {
            if (vehicleRepo.count() > 0) { // 26.10.20 택배차 사진 추가분: 비어 있는 번호만 더 등록
                for (String p : List.of("부산90배7690", "부산90배7568", "부산90배9151", "부산90배9517"))
                    if (!vehicleRepo.existsById(p)) vehicleRepo.save(new HubVehicle(p, "COURIER", "S카고", "권역 출고 택배차"));
                return;
            }
            List<HubVehicle> seed = new ArrayList<>();
            for (String p : List.of("서울85자6013", "경기90자8118", "경기92아5439", "인천98바2578", "대구80아8605", "울산81사5366", "경북86아1065"))
                seed.add(new HubVehicle(p, "CARGO", "S카고", "입고 화물차"));
            for (String p : List.of("부산90배8407", "경남99바4892", "대구80자6850", "부산91자5787"))
                seed.add(new HubVehicle(p, "COURIER", "S카고", "권역 출고 택배차"));
            // 부산91자4739 는 일부러 등록하지 않음 → 미등록 차단 시연
            vehicleRepo.saveAll(seed);
        } catch (Exception ignored) { /* 테이블이 아직 없으면 다음 실행 때 */ }
    }

    static String norm(String p) { return p == null ? null : p.replace(" ", "").trim(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Double dbl(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    // 소속 업체: 등록 화물차(trucks) → 기업(companies) 에서 먼저 찾고, 없으면 허브 등록 차량(hub_vehicles.carrier)
    private String companyOf(String plate) {
        String p = norm(plate);
        if (p == null || p.isEmpty()) return null;
        try {
            List<String> r = jdbc.queryForList("SELECT c.company_name FROM trucks t JOIN companies c ON c.company_id = t.company_id "
                    + "WHERE REPLACE(t.vehicle_no,' ','') = ? LIMIT 1", String.class, p);
            if (!r.isEmpty() && r.get(0) != null && !r.get(0).isBlank()) return r.get(0);
        } catch (Exception ignore) { /* 테이블/컬럼이 다르면 다음 경로 */ }
        try {
            List<String> r = jdbc.queryForList("SELECT carrier FROM hub_vehicles WHERE plate_no = ? LIMIT 1", String.class, p);
            if (!r.isEmpty() && r.get(0) != null && !r.get(0).isBlank()) return r.get(0);
        } catch (Exception ignore) { }
        return null;
    }

    VehicleView view(HubVehicleLog l) {
        String plate = l.getRecognizedPlate() != null ? l.getRecognizedPlate() : l.getPhotoPlate();
        return new VehicleView(l.getLogId(), l.getDirection(), l.getRecognizedPlate(), l.getPhotoPlate(), l.getVehicleKind(),
                l.getDecision(), l.getLane(), l.getDockNo(), l.getOcrConfidence(), l.getOcrEngine(), l.getImageUrl(), l.getBoxCount(),
                l.getDestHub(), l.getCreatedAt(), l.getSealStatus(), l.getSealInput(), companyOf(plate));
    }

    // 26.10.29 구간 관제 연동: 곤지암에 도착했는데 아직 허브 입차 기록이 없는 차량 중 가장 먼저 도착한 번호 (도착 순서 = 입차 순서)
    //  구간 관제 서버(toll_demo)가 남기는 toll_trip_quotes · toll_trip_positions(route_status = ARRIVED) 를 그대로 읽는다. 없거나 테이블이 없으면 null → 예전처럼 허브가 알아서 받는다
    private final Map<String, Long> reserved = new java.util.concurrent.ConcurrentHashMap<>(); // 동시에 두 도크가 같은 차를 집지 않게 60초 예약

    private synchronized String nextArrival(Long hub) {
        long now = System.currentTimeMillis();
        reserved.values().removeIf(t -> now - t > 60_000);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT q.vehicle_no FROM toll_trip_quotes q JOIN toll_trip_positions p ON p.trip_key = q.trip_key "
                    + "WHERE p.route_status = 'ARRIVED' AND p.updated_at > now() - interval '3 hours' "
                    + "AND NOT EXISTS (SELECT 1 FROM hub_vehicle_logs l WHERE l.hub_id = ? AND l.direction = 'ENTRY' "
                    + "AND REPLACE(COALESCE(l.recognized_plate, l.photo_plate), ' ', '') = REPLACE(q.vehicle_no, ' ', '') AND l.created_at >= p.updated_at - interval '1 minute') "
                    + "ORDER BY p.updated_at ASC, q.trip_key ASC LIMIT 20", hub);
            for (Map<String, Object> r : rows) {
                String p = norm(str(r.get("vehicle_no")));
                if (p != null && !p.isEmpty() && !reserved.containsKey(p)) { reserved.put(p, now); return p; }
            }
        } catch (Exception ignore) { /* 구간 관제 테이블이 없으면 연동 없이 */ }
        return null;
    }

    // S카고 에 등록된 화물차(trucks)인지 — 구간 관제 대기열로 들어온 차량은 hub_vehicles 에 없어도 입차를 허용
    private boolean truckRegistered(String plate) {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM trucks WHERE REPLACE(vehicle_no, ' ', '') = ?", Integer.class, norm(plate));
            return n != null && n > 0;
        } catch (Exception e) { return false; }
    }

    // 지금 허브 안에 있는 차 (30분 안에 입차 허용 + 아직 출차 기록 없음) → 같은 사진이 겹치지 않게 OCR 에 넘긴다
    private Set<String> inside(Long hub) {
        Set<String> in = new LinkedHashSet<>();
        for (HubVehicleLog l : logRepo.findByHubIdAndCreatedAtAfterOrderByLogIdAsc(hub, OffsetDateTime.now().minusMinutes(30))) {
            String p = norm(l.getPhotoPlate());
            if (p == null) continue;
            if ("ENTRY".equals(l.getDirection()) && "ALLOW".equals(l.getDecision())) in.add(p); else if ("EXIT".equals(l.getDirection())) in.remove(p);
        }
        return in;
    }

    @SuppressWarnings("unchecked")
    public VehicleView scan(Long hubId, VehicleScanRequest req) {
        Long hub = amr.hub(hubId, Access.MANAGE);
        String dir = "EXIT".equalsIgnoreCase(req.direction()) ? "EXIT" : "ENTRY";
        String kind = "COURIER".equalsIgnoreCase(req.kind()) ? "COURIER" : "CARGO";
        String wantPlate = null;
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(ocrBase + "/hub/scan")
                .queryParam("direction", dir).queryParam("kind", kind);
        if ("EXIT".equals(dir)) {
            if (req.vehicleNo() == null || req.vehicleNo().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "출차에는 vehicleNo 가 필요합니다");
            b.queryParam("vehicleNo", norm(req.vehicleNo()));
        } else {
            Set<String> in = inside(hub);
            if (!in.isEmpty()) b.queryParam("exclude", String.join(",", in));
            if ("CARGO".equals(kind)) { wantPlate = nextArrival(hub); if (wantPlate != null) b.queryParam("want", wantPlate); } // 26.10.29 구간 관제에서 먼저 도착한 차
        }
        URI uri = b.encode().build().toUri();
        Map<String, Object> r;
        try {
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.set("X-Gate-Token", ocrToken); // OCR 서버가 토큰 없는 호출을 거부함 (환경변수 GATE_API_TOKEN)
            r = rest.exchange(uri, org.springframework.http.HttpMethod.POST, new org.springframework.http.HttpEntity<>(h), Map.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 서버 오류 " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 서버(gate_api.py, " + ocrBase + ") 연결 실패");
        }
        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 응답 없음");

        String plate = norm(str(r.get("recognizedPlate")));
        Double conf = dbl(r.get("ocrConfidence"));
        HubVehicleLog log = new HubVehicleLog();
        log.setHubId(hub); log.setDirection(dir); log.setRecognizedPlate(plate); log.setPhotoPlate(norm(str(r.get("photoPlate"))));
        log.setOcrConfidence(conf == null ? null : Math.round(conf * 1000) / 10.0); // 0.987 → 98.7 (%)
        log.setOcrEngine(str(r.get("engine"))); log.setImageUrl(str(r.get("imageUrl")));
        log.setLane("COURIER".equals(kind) ? "OUT" : "IN"); log.setDockNo(req.dock()); log.setCreatedAt(OffsetDateTime.now());

        String confTxt = log.getOcrConfidence() == null ? "" : " (" + log.getOcrConfidence() + "%)";
        if ("ENTRY".equals(dir)) {
            Optional<HubVehicle> reg = plate == null ? Optional.empty() : vehicleRepo.findById(plate);
            // 26.10.29 구간 관제 대기열로 들어온 차량(번호가 지정한 번호와 같음)은 S카고 등록 화물차이면 허브 등록이 없어도 허용
            boolean viaQueue = wantPlate != null && plate != null && plate.equals(norm(wantPlate));
            if (reg.isEmpty() && viaQueue && truckRegistered(plate)) {
                String co = companyOf(plate);
                reg = Optional.of(new HubVehicle(plate, "CARGO", co == null ? "S카고" : co, "구간 관제 연동 화물차"));
            }
            log.setVehicleKind(reg.map(HubVehicle::getVehicleKind).orElse("UNKNOWN"));
            // 26.10.20 등록 화물차인데 기준 씰 번호가 없으면 게이트에서 대기 + 긴급 알림
            boolean sealMissing = reg.isPresent() && "CARGO".equals(reg.get().getVehicleKind())
                    && refSeal(plate) == null;
            log.setDecision(!reg.isPresent() ? "BLOCK" : sealMissing ? "HOLD" : "ALLOW");
            if (sealMissing) {
                log.setSealStatus("MISSING");
                amr.event(hub, null, "CRIT", "씰 미등록 차량 게이트 대기 · " + plate + confTxt + " (기사 앱에서 씰 등록 필요)");
            } else if (reg.isPresent()) {
                boolean courier = "COURIER".equals(reg.get().getVehicleKind());
                if (!courier) log.setSealStatus("PENDING"); // 도크 도착 후 씰 확인 대기
                String to = courier ? "출고 D" + (req.dock() == null ? "" : req.dock() + 4) : "입고 D" + (req.dock() == null ? "" : req.dock() + 1);
                amr.event(hub, null, "INFO", "입차 OCR " + plate + confTxt + " · " + (courier ? "택배차" : "화물차") + " → " + to);
            } else {
                amr.event(hub, null, "CRIT", plate == null ? "입차 번호판 판독 불가 · 차단 (사진 " + log.getPhotoPlate() + ")"
                        : "미등록 차량 차단 · " + plate + confTxt);
            }
        } else {
            String expected = norm(req.vehicleNo());
            log.setVehicleKind(vehicleRepo.findById(expected).map(HubVehicle::getVehicleKind).orElse("UNKNOWN"));
            log.setBoxCount(req.boxCount());
            log.setBarcodes(req.barcodes() == null ? null : String.join(",", req.barcodes()));
            log.setDestHub(req.destHub());
            boolean ok = plate != null && plate.equals(expected);
            log.setDecision(ok ? "EXIT_OK" : "MISMATCH");
            String n = req.boxCount() == null ? "" : " · " + req.boxCount() + "건";
            amr.event(hub, null, ok ? "DISPATCH" : "WARN", ok ? "출차 OCR " + plate + confTxt + " · " + (req.destHub() == null ? "" : req.destHub() + "행") + n
                    : "출차 번호 불일치 · 입차 " + expected + " / 인식 " + (plate == null ? "판독 불가" : plate));
        }
        return view(logRepo.save(log));
    }

    // 26.10.16 인식 사진을 백엔드(8080)를 거쳐 내준다
    //  - 휴대폰이 OCR 서버(8001)에 직접 붙지 않아도 됨 (노트북 방화벽에 8001을 열 필요 없음)
    //  - 사진 주소는 DB에 남은 OCR 서버 주소의 경로만 쓰고, 서버는 설정된 ocrBase 로 고정 (다른 주소로 못 보냄)
    public byte[] photo(Long id) {
        HubVehicleLog l = logRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "기록 없음"));
        access.requireHub(l.getHubId(), Access.VIEW);
        String u = l.getImageUrl();
        int i = u == null ? -1 : u.indexOf("/hub-images/");
        if (i < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사진 없음");
        URI uri = UriComponentsBuilder.fromUriString(ocrBase + u.substring(i)).build().encode().toUri();
        try {
            byte[] b = rest.getForObject(uri, byte[].class);
            if (b == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사진 없음");
            return b;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 서버에서 사진을 가져오지 못했습니다");
        }
    }

    // 26.10.20 씰 확인: 도크에 도착한 차의 씰 번호를 스캔 · 입력해 DB 기준 번호와 비교한다
    //  일치 → 씰 확인 완료 / 불일치 · 기준 없음 → 하차 대기 + 긴급 알림 (관리자 앱)
    public VehicleView sealCheck(Long id, String input) {
        HubVehicleLog l = logRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "기록 없음"));
        Long hub = l.getHubId();
        access.requireHub(hub, Access.MANAGE);
        if (!"ENTRY".equals(l.getDirection()) || l.getRecognizedPlate() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "씰은 입차 기록에서만 확인합니다");
        String in = input == null ? "" : input.replace(" ", "").trim().toUpperCase();
        if (in.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "씰 번호를 입력하세요");
        String plate = l.getRecognizedPlate();
        String ref = refSeal(plate);
        l.setSealInput(in);
        sealEvent(plate, in);
        if (ref == null || ref.isBlank()) {
            l.setSealStatus("MISSING"); l.setDecision("HOLD");
            amr.event(hub, null, "CRIT", "기준 씰 번호 없음 · 하차 보류 · " + plate + " (수동 확인 필요)");
        } else if (ref.equalsIgnoreCase(in)) {
            l.setSealStatus("OK");
            amr.event(hub, null, "INFO", "씰 대조 일치 · 하차 가능 · " + plate + " · " + in);
        } else {
            l.setSealStatus("MISMATCH"); l.setDecision("HOLD");
            amr.event(hub, null, "CRIT", "씰 불일치 · 하차 보류 · " + plate + " · 입력 " + in);
        }
        return view(logRepo.save(l));
    }

    // 26.10.29 허브 화면 "입차 대기열": 구간 관제 도착 순서 그대로. 최근 입차 완료 2대 + 아직 입차 전인 차(맨 앞이 다음 차례)
    public List<Map<String, Object>> arrivalQueue(Long hubId) {
        Long hub = amr.hub(hubId, Access.VIEW);
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT q.vehicle_no, (SELECT COUNT(*) FROM hub_vehicle_logs l WHERE l.hub_id = ? AND l.direction = 'ENTRY' "
                    + "AND REPLACE(COALESCE(l.recognized_plate, l.photo_plate), ' ', '') = REPLACE(q.vehicle_no, ' ', '') "
                    + "AND l.created_at >= p.updated_at - interval '1 minute') AS entered "
                    + "FROM toll_trip_quotes q JOIN toll_trip_positions p ON p.trip_key = q.trip_key "
                    + "WHERE p.route_status = 'ARRIVED' AND p.updated_at > now() - interval '3 hours' "
                    + "ORDER BY p.updated_at ASC, q.trip_key ASC LIMIT 40", hub);
            List<Map<String, Object>> done = new ArrayList<>(), wait = new ArrayList<>();
            int seq = 0;
            for (Map<String, Object> r : rows) {
                seq++;
                String plate = norm(str(r.get("vehicle_no")));
                if (plate == null || plate.isEmpty()) continue;
                boolean in = r.get("entered") instanceof Number n && n.longValue() > 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("seq", seq); m.put("plate", plate); m.put("state", in ? "ENTERED" : "WAIT");
                (in ? done : wait).add(m);
            }
            for (int i = Math.max(0, done.size() - 2); i < done.size(); i++) out.add(done.get(i));
            if (!wait.isEmpty()) wait.get(0).put("state", "NEXT");
            for (int i = 0; i < wait.size() && i < 4; i++) out.add(wait.get(i));
        } catch (Exception ignore) { /* 구간 관제 테이블이 없으면 빈 목록 */ }
        return out;
    }

    public List<VehicleView> recent(Long hubId, int limit) {
        return logRepo.findByHubIdOrderByLogIdDesc(amr.hub(hubId, Access.VIEW), PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream().map(this::view).toList();
    }

    // 등록 차량부는 허브 공통 (조회 권한만 확인)
    public List<RegistryView> registry() {
        amr.hub(null, Access.VIEW);
        return vehicleRepo.findAll().stream().map(v -> new RegistryView(v.getPlateNo(), v.getVehicleKind(), v.getCarrier(), v.getMemo())).toList();
    }

}
