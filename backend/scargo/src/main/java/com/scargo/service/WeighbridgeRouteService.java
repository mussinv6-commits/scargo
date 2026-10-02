package com.scargo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.dto.WeighbridgeRouteResponse;
import com.scargo.dto.WeighbridgeRouteResponse.Point;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.entity.Truck;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.GateRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 26.10.02 추가: 검사소 화면 지도용 경로 계산 (방식 B - 지점끼리 선으로 연결)
 *
 *  출발(origin)    : planned_route 의 origin_gate/start_gate → 없으면 게이트 OCR 이 찍힌 게이트
 *                    → 그게 목적지와 같으면 다른 활성 게이트(항만 진입 게이트로 간주)
 *  검사소          : application.yml 의 scargo.weighbridge.lat/lng → 없으면 출발과 목적지 사이 35% 지점
 *  경유지(waypoints): planned_route 의 waypoints/path/route 배열 ([lat,lng] 또는 {lat,lng})
 *  목적지          : planned_route 의 destination_gate ("Gate-DEFG-01 (DEFG야드 출입구)" → Gate-DEFG-01)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeighbridgeRouteService {

    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository;
    private final TruckRepository truckRepository;
    private final ObjectMapper objectMapper;

    @Value("${scargo.weighbridge.station-code:WB-01}")
    private String stationCode;

    // 검사소 실제 좌표를 알면 application.yml 에 넣으면 됨 (비워두면 자동 배치)
    @Value("${scargo.weighbridge.lat:#{null}}")
    private Double wbLat;

    @Value("${scargo.weighbridge.lng:#{null}}")
    private Double wbLng;

    public WeighbridgeRouteResponse getRoute(String vehicleNo, Long gateLogId) {
        String no = vehicleNo == null ? null : vehicleNo.trim().replace(" ", "");
        JsonNode plan = readPlannedRoute(no);

        List<Gate> activeGates = gateRepository.findByIsActiveTrue().stream()
                .sorted(Comparator.comparing(Gate::getGateId))
                .toList();

        // 목적지
        Gate destGate = findGate(text(plan, "destination_gate", "destinationGate", "dest_gate"));

        // 출발: 계획 → 게이트 OCR 기록 → 다른 활성 게이트
        Gate originGate = findGate(text(plan, "origin_gate", "originGate", "start_gate", "entry_gate"));
        if (originGate == null && gateLogId != null) {
            originGate = gateLogRepository.findById(gateLogId).map(GateLog::getGate).orElse(null);
        }
        if (originGate == null || sameGate(originGate, destGate)) {
            Gate dest = destGate;
            originGate = activeGates.stream()
                    .filter(g -> hasCoord(g) && !sameGate(g, dest))
                    .filter(g -> "IN".equalsIgnoreCase(g.getGateType()) || "BOTH".equalsIgnoreCase(g.getGateType()))
                    .findFirst()
                    .orElse(activeGates.stream().filter(g -> hasCoord(g) && !sameGate(g, dest)).findFirst().orElse(originGate));
        }

        Point origin = toPoint(originGate);
        Point destination = toPoint(destGate);
        List<Point> waypoints = readWaypoints(plan);
        Point weighbridge = weighbridgePoint(origin, destination);

        Gate o = originGate;
        Gate d = destGate;
        List<Point> others = activeGates.stream()
                .filter(g -> hasCoord(g) && !sameGate(g, o) && !sameGate(g, d))
                .map(this::toPoint)
                .toList();

        return new WeighbridgeRouteResponse(no, origin, weighbridge, waypoints, destination, others);
    }

    // ---- 내부 도우미 ----

    private JsonNode readPlannedRoute(String vehicleNo) {
        if (vehicleNo == null || vehicleNo.isBlank()) return null;
        String json = truckRepository.findById(vehicleNo).map(Truck::getPlannedRoute).orElse(null);
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** "Gate-DEFG-01 (DEFG야드 출입구)" 처럼 설명이 붙어 있어도 앞의 코드만 떼서 찾음 */
    private Gate findGate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String code = raw.trim().split("\\s+", 2)[0];
        return gateRepository.findByGateCode(code).orElse(null);
    }

    private Point weighbridgePoint(Point origin, Point destination) {
        String name = "검사소 " + stationCode;
        if (wbLat != null && wbLng != null) {
            return new Point(stationCode, name, wbLat, wbLng);
        }
        if (origin != null && origin.getLat() != null && destination != null && destination.getLat() != null) {
            double t = 0.35; // 진입 게이트 쪽에 가깝게
            double lat = origin.getLat() + (destination.getLat() - origin.getLat()) * t;
            double lng = origin.getLng() + (destination.getLng() - origin.getLng()) * t;
            return new Point(stationCode, name, lat, lng);
        }
        Point base = origin != null && origin.getLat() != null ? origin : destination;
        if (base != null && base.getLat() != null) {
            return new Point(stationCode, name, base.getLat() + 0.0012, base.getLng() + 0.0016);
        }
        return new Point(stationCode, name, null, null);
    }

    private List<Point> readWaypoints(JsonNode plan) {
        List<Point> out = new ArrayList<>();
        if (plan == null) return out;
        for (String key : new String[]{"waypoints", "path", "route", "coordinates"}) {
            JsonNode arr = plan.path(key);
            if (!arr.isArray()) continue;
            int i = 1;
            for (JsonNode n : arr) {
                Double lat = null;
                Double lng = null;
                String name = null;
                if (n.isArray() && n.size() >= 2) {
                    lat = n.get(0).asDouble();
                    lng = n.get(1).asDouble();
                } else if (n.isObject()) {
                    lat = num(n, "lat", "latitude");
                    lng = num(n, "lng", "lon", "longitude");
                    name = text(n, "name", "label");
                }
                if (lat != null && lng != null) {
                    out.add(new Point("WP" + i, name != null ? name : "경유지 " + i, lat, lng));
                    i++;
                }
            }
            if (!out.isEmpty()) break;
        }
        return out;
    }

    private Point toPoint(Gate g) {
        if (g == null) return null;
        Double lat = g.getLatitude() != null ? g.getLatitude().doubleValue() : null;
        Double lng = g.getLongitude() != null ? g.getLongitude().doubleValue() : null;
        return new Point(g.getGateCode(), g.getGateName(), lat, lng);
    }

    private static boolean hasCoord(Gate g) {
        return g != null && g.getLatitude() != null && g.getLongitude() != null;
    }

    private static boolean sameGate(Gate a, Gate b) {
        return a != null && b != null && Objects.equals(a.getGateId(), b.getGateId());
    }

    private static String text(JsonNode n, String... keys) {
        if (n == null) return null;
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
        }
        return null;
    }

    private static Double num(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isNumber()) return v.asDouble();
            if (v.isTextual()) {
                try { return Double.parseDouble(v.asText().trim()); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }
}
