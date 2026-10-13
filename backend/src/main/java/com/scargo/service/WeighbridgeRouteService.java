package com.scargo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.dto.WeighbridgeRouteResponse;
import com.scargo.dto.WeighbridgeRouteResponse.Point;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.entity.LoadingLocation;
import com.scargo.entity.Truck;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.GateRepository;
import com.scargo.repository.LoadingLocationRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeighbridgeRouteService {

    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository;
    private final TruckRepository truckRepository;
    private final LoadingLocationRepository loadingLocationRepository;
    private final ObjectMapper objectMapper;

    @Value("${scargo.weighbridge.station-code:WB-01}")
    private String stationCode;

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

        // planned_route의 목적지 게이트 조회
        Gate destGate = findGate(
                text(plan, "destination_gate", "destinationGate", "dest_gate")
        );

        // planned_route의 진입 게이트 조회
        Gate originGate = findGate(
                text(plan, "origin_gate", "originGate", "start_gate", "entry_gate")
        );

        // planned_route에 진입 게이트가 없으면 OCR 게이트 사용
        if (originGate == null && gateLogId != null) {
            originGate = gateLogRepository.findById(gateLogId)
                    .map(GateLog::getGate)
                    .orElse(null);
        }

        // 그래도 진입 게이트가 없으면 활성 IN/BOTH 게이트 사용
        if (originGate == null) {
            originGate = activeGates.stream()
                    .filter(WeighbridgeRouteService::hasCoord)
                    .filter(g ->
                            "IN".equalsIgnoreCase(g.getGateType())
                                    || "BOTH".equalsIgnoreCase(g.getGateType()))
                    .findFirst()
                    .orElse(null);
        }

        // origin과 destination이 같아도 planned_route 값을 그대로 사용
        Point origin = toPoint(originGate);
        Point destination = toPoint(destGate);

        // 검사소 위치
        Point weighbridge = weighbridgePoint(origin, destination);

        // planned_route.container.loading_location_id의 적재 위치
        List<Point> waypoints = readLoadingLocationWaypoint(plan);

        // planned_route에 별도 waypoints/path/route가 있으면 추가
        List<Point> extraWaypoints = readWaypoints(plan);
        if (!extraWaypoints.isEmpty()) {
            waypoints.addAll(extraWaypoints);
        }

        Gate finalOriginGate = originGate;
        Gate finalDestGate = destGate;

        List<Point> others = activeGates.stream()
                .filter(WeighbridgeRouteService::hasCoord)
                .filter(g -> !sameGate(g, finalOriginGate))
                .filter(g -> !sameGate(g, finalDestGate))
                .map(this::toPoint)
                .toList();

        return new WeighbridgeRouteResponse(
                no,
                origin,
                weighbridge,
                waypoints,
                destination,
                others
        );
    }

    // 차량의 planned_route JSON 조회
    private JsonNode readPlannedRoute(String vehicleNo) {
        if (vehicleNo == null || vehicleNo.isBlank()) {
            return null;
        }

        String json = truckRepository.findById(vehicleNo)
                .map(Truck::getPlannedRoute)
                .orElse(null);

        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    // "Gate-DEFG-01 (DEFG야드 출입구)"에서 Gate-DEFG-01만 추출
    private Gate findGate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String code = raw.trim().split("\\s+", 2)[0];

        return gateRepository.findByGateCode(code)
                .orElse(null);
    }

    // planned_route.container.loading_location_id의 실제 적재 위치 조회
    private List<Point> readLoadingLocationWaypoint(JsonNode plan) {
        List<Point> result = new ArrayList<>();

        if (plan == null) {
            return result;
        }

        JsonNode container = plan.path("container");
        if (container.isMissingNode() || container.isNull()) {
            return result;
        }

        JsonNode locationNode = container.path("loading_location_id");
        if (locationNode.isMissingNode() || locationNode.isNull()) {
            return result;
        }

        Long locationId;

        try {
            if (locationNode.isNumber()) {
                locationId = locationNode.asLong();
            } else {
                locationId = Long.parseLong(locationNode.asText().trim());
            }
        } catch (Exception e) {
            return result;
        }

        LoadingLocation location = loadingLocationRepository.findById(locationId)
                .orElse(null);

        if (location == null
                || location.getLatitude() == null
                || location.getLongitude() == null) {
            return result;
        }

        String sector = location.getSector();

        if (sector == null || sector.isBlank()) {
            sector = text(container, "loading_sector");
        }

        if (sector == null || sector.isBlank()) {
            sector = "적재 위치";
        }

        result.add(new Point(
                "LOAD-" + locationId,
                sector,
                location.getLatitude().doubleValue(),
                location.getLongitude().doubleValue()
        ));

        return result;
    }

    // 검사소 좌표 계산
    private Point weighbridgePoint(Point origin, Point destination) {
        String name = "검사소 " + stationCode;

        // application.yml에 검사소 좌표가 있으면 실제 좌표 사용
        if (wbLat != null && wbLng != null) {
            return new Point(stationCode, name, wbLat, wbLng);
        }

        // 진입/목적지 좌표가 서로 다르면 두 지점 사이에 검사소 임시 배치
        if (origin != null
                && destination != null
                && origin.getLat() != null
                && origin.getLng() != null
                && destination.getLat() != null
                && destination.getLng() != null) {

            boolean samePosition =
                    Math.abs(origin.getLat() - destination.getLat()) < 0.000001
                            && Math.abs(origin.getLng() - destination.getLng()) < 0.000001;

            if (!samePosition) {
                double t = 0.35;

                double lat = origin.getLat()
                        + (destination.getLat() - origin.getLat()) * t;

                double lng = origin.getLng()
                        + (destination.getLng() - origin.getLng()) * t;

                return new Point(stationCode, name, lat, lng);
            }
        }

        // 진입과 목적지가 같으면 게이트 근처에 검사소 임시 배치
        Point base = origin != null && origin.getLat() != null
                ? origin
                : destination;

        if (base != null && base.getLat() != null && base.getLng() != null) {
            return new Point(
                    stationCode,
                    name,
                    base.getLat() + 0.0012,
                    base.getLng() + 0.0016
            );
        }

        return new Point(stationCode, name, null, null);
    }

    // planned_route의 별도 waypoints/path/route/coordinates 조회
    private List<Point> readWaypoints(JsonNode plan) {
        List<Point> out = new ArrayList<>();

        if (plan == null) {
            return out;
        }

        for (String key : new String[]{"waypoints", "path", "route", "coordinates"}) {
            JsonNode arr = plan.path(key);

            if (!arr.isArray()) {
                continue;
            }

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
                    out.add(new Point(
                            "WP" + i,
                            name != null ? name : "경유지 " + i,
                            lat,
                            lng
                    ));
                    i++;
                }
            }

            if (!out.isEmpty()) {
                break;
            }
        }

        return out;
    }

    // Gate를 지도 Point로 변환
    private Point toPoint(Gate gate) {
        if (gate == null) {
            return null;
        }

        Double lat = gate.getLatitude() != null
                ? gate.getLatitude().doubleValue()
                : null;

        Double lng = gate.getLongitude() != null
                ? gate.getLongitude().doubleValue()
                : null;

        return new Point(
                gate.getGateCode(),
                gate.getGateName(),
                lat,
                lng
        );
    }

    // 게이트 좌표 존재 여부
    private static boolean hasCoord(Gate gate) {
        return gate != null
                && gate.getLatitude() != null
                && gate.getLongitude() != null;
    }

    // 동일 게이트 여부
    private static boolean sameGate(Gate a, Gate b) {
        return a != null
                && b != null
                && a.getGateId().equals(b.getGateId());
    }

    // JSON 문자열 값 조회
    private static String text(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }

        for (String key : keys) {
            JsonNode value = node.path(key);

            if (!value.isMissingNode()
                    && !value.isNull()
                    && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }

        return null;
    }

    // JSON 숫자 값 조회
    private static Double num(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);

            if (value.isNumber()) {
                return value.asDouble();
            }

            if (value.isTextual()) {
                try {
                    return Double.parseDouble(value.asText().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }
}