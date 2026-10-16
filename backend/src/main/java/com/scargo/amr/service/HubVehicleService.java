package com.scargo.amr.service;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.entity.HubVehicle;
import com.scargo.amr.entity.HubVehicleLog;
import com.scargo.amr.repository.HubVehicleLogRepository;
import com.scargo.amr.repository.HubVehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
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
@Service
public class HubVehicleService implements ApplicationRunner {

    private final AmrService amr;
    private final HubVehicleRepository vehicleRepo;
    private final HubVehicleLogRepository logRepo;
    private final String ocrBase;
    private final RestTemplate rest;

    public HubVehicleService(AmrService amr, HubVehicleRepository vehicleRepo, HubVehicleLogRepository logRepo,
                             @Value("${amr.ocr.base:http://localhost:8001}") String ocrBase) {
        this.amr = amr; this.vehicleRepo = vehicleRepo; this.logRepo = logRepo; this.ocrBase = ocrBase;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(20000); // CPU 인식은 첫 호출에 몇 초 걸릴 수 있음
        this.rest = new RestTemplate(f);
    }

    // ── 등록 차량 기본값 (비어 있을 때 한 번만) · 사진 폴더 python/허브_수신함 과 같은 번호 ──
    @Override
    public void run(ApplicationArguments args) {
        try {
            if (vehicleRepo.count() > 0) return;
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

    static VehicleView view(HubVehicleLog l) {
        return new VehicleView(l.getLogId(), l.getDirection(), l.getRecognizedPlate(), l.getPhotoPlate(), l.getVehicleKind(),
                l.getDecision(), l.getLane(), l.getDockNo(), l.getOcrConfidence(), l.getOcrEngine(), l.getImageUrl(), l.getBoxCount(),
                l.getDestHub(), l.getCreatedAt());
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
    public VehicleView scan(VehicleScanRequest req) {
        Long hub = amr.hubId();
        String dir = "EXIT".equalsIgnoreCase(req.direction()) ? "EXIT" : "ENTRY";
        String kind = "COURIER".equalsIgnoreCase(req.kind()) ? "COURIER" : "CARGO";
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(ocrBase + "/hub/scan")
                .queryParam("direction", dir).queryParam("kind", kind);
        if ("EXIT".equals(dir)) {
            if (req.vehicleNo() == null || req.vehicleNo().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "출차에는 vehicleNo 가 필요합니다");
            b.queryParam("vehicleNo", norm(req.vehicleNo()));
        } else {
            Set<String> in = inside(hub);
            if (!in.isEmpty()) b.queryParam("exclude", String.join(",", in));
        }
        URI uri = b.encode().build().toUri();
        Map<String, Object> r;
        try {
            r = rest.postForObject(uri, null, Map.class);
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
            log.setVehicleKind(reg.map(HubVehicle::getVehicleKind).orElse("UNKNOWN"));
            log.setDecision(reg.isPresent() ? "ALLOW" : "BLOCK");
            if (reg.isPresent()) {
                boolean courier = "COURIER".equals(reg.get().getVehicleKind());
                String to = courier ? "출고 D" + (req.dock() == null ? "" : req.dock() + 4) : "입고 D" + (req.dock() == null ? "" : req.dock() + 1);
                amr.event(null, "INFO", "입차 OCR " + plate + confTxt + " · " + (courier ? "택배차" : "화물차") + " → " + to);
            } else {
                amr.event(null, "CRIT", plate == null ? "입차 번호판 판독 불가 · 차단 (사진 " + log.getPhotoPlate() + ")"
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
            amr.event(null, ok ? "DISPATCH" : "WARN", ok ? "출차 OCR " + plate + confTxt + " · " + (req.destHub() == null ? "" : req.destHub() + "행") + n
                    : "출차 번호 불일치 · 입차 " + expected + " / 인식 " + (plate == null ? "판독 불가" : plate));
        }
        return view(logRepo.save(log));
    }

    // 26.10.16 인식 사진을 백엔드(8080)를 거쳐 내준다
    //  - 휴대폰이 OCR 서버(8001)에 직접 붙지 않아도 됨 (노트북 방화벽에 8001을 열 필요 없음)
    //  - 사진 주소는 DB에 남은 OCR 서버 주소의 경로만 쓰고, 서버는 설정된 ocrBase 로 고정 (다른 주소로 못 보냄)
    public byte[] photo(Long id) {
        HubVehicleLog l = logRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "기록 없음"));
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

    public List<VehicleView> recent(int limit) {
        return logRepo.findByHubIdOrderByLogIdDesc(amr.hubId(), PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream().map(HubVehicleService::view).toList();
    }

    public List<RegistryView> registry() {
        return vehicleRepo.findAll().stream().map(v -> new RegistryView(v.getPlateNo(), v.getVehicleKind(), v.getCarrier(), v.getMemo())).toList();
    }

}
