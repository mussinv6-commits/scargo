package com.scargo.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.app.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.10.03 2차: 경로 계산 엔진 모음. RouteService 가 설정된 순서대로 시도하고, 실패하면 다음 엔진으로 넘어간다.
 *
 *  - fixed    : fix_1500 지정 경로 (classpath:routes/*.json, HTML 기본 경로). 맞는 출발·도착이 아니면 다음 엔진
 *  - tmap     : TMAP 화물차 경로안내 (높이·중량 반영, 한글 안내문). 앱키 필요, 무료 일일 한도 있음
 *  - valhalla : 직접 띄운 Valhalla 서버 (오픈소스, truck 프로필로 높이·중량 반영, 호출 한도 없음)
 *  - osrm     : OSRM 서버 (승용차 기준, 공개 데모 서버는 테스트용)
 *  - straight : 외부 호출 없는 직선 경로 (턴바이턴 안내 없음)
 *
 * 모든 엔진은 같은 모양(BaseRoute)으로 돌려준다. 안내 지점(steps)의 type 은 아래 공통 코드로 맞춘다.
 *  DEPART ARRIVE STRAIGHT LEFT RIGHT SLIGHT_LEFT SLIGHT_RIGHT SHARP_LEFT SHARP_RIGHT UTURN
 *  RAMP_LEFT RAMP_RIGHT EXIT_LEFT EXIT_RIGHT KEEP_LEFT KEEP_RIGHT MERGE ROUNDABOUT TOLL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteProviders {

    /** 엔진 결과. path = [[lat,lng],...] (단순화 전), steps = 안내 지점 */
    public record BaseRoute(String provider, double distanceM, double durationS, List<double[]> path, List<Step> steps) {
    }

    /** 안내 지점. text 가 있으면(TMAP) 그대로 쓰고, 없으면 RouteService 가 type·roadName 으로 한글 문장을 만든다 */
    public record Step(double lat, double lng, String type, String roadName, String text) {
    }

    /** 차량 조건 (화물차 경로용) */
    public record Truck(BigDecimal heightM, Integer grossWeightKg, Integer axleCount) {
    }

    private final ObjectMapper objectMapper;
    private final FixedRoutes fixedRoutes; // fix_1500: 지정 경로(HTML 기본 경로)

    @Value("${scargo.route.osrm-url:https://router.project-osrm.org}")
    private String osrmUrl;

    @Value("${scargo.route.truck-time-factor:1.15}")
    private double truckTimeFactor;

    @Value("${scargo.route.straight-speed-kmh:65}")
    private double straightSpeedKmh;

    @Value("${scargo.route.tmap.app-key:}")
    private String tmapAppKey;

    @Value("${scargo.route.tmap.url:https://apis.openapi.sk.com/tmap/truck/routes?version=1&format=json}")
    private String tmapUrl;

    @Value("${scargo.route.valhalla-url:http://localhost:8002}")
    private String valhallaUrl;

    @Value("${scargo.route.truck.width-m:2.5}")
    private double truckWidthM;

    @Value("${scargo.route.truck.length-m:16.7}")
    private double truckLengthM;

    public BaseRoute route(String provider, double fromLat, double fromLng, double toLat, double toLng, Truck truck) {
        try {
            return switch (provider) {
                case "fixed" -> fixedRoutes.route(fromLat, fromLng, toLat, toLng);
                case "tmap" -> tmap(fromLat, fromLng, toLat, toLng, truck);
                case "valhalla" -> valhalla(fromLat, fromLng, toLat, toLng, truck);
                case "osrm" -> osrm(fromLat, fromLng, toLat, toLng);
                case "straight" -> straight(fromLat, fromLng, toLat, toLng);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("[{}] 경로 계산 실패 → 다음 엔진으로: {}", provider, e.getMessage());
            return null;
        }
    }

    private RestClient client(String baseUrl, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(readTimeoutMs);
        RestClient.Builder b = RestClient.builder().requestFactory(factory);
        return baseUrl == null ? b.build() : b.baseUrl(baseUrl).build();
    }

    // ------------------------------------------------------------------ TMAP

    /**
     * TMAP 화물차 경로안내. 응답은 GeoJSON FeatureCollection
     *  - Point 피처: 안내 지점 (properties.turnType, description), 첫 Point 에 totalDistance(m), totalTime(s)
     *  - LineString 피처: 경로 선
     * ※ 화물차 파라미터 이름·단위는 SK open API 문서(화물차 경로안내)에서 꼭 확인하세요. 아래는 cm·kg 기준.
     */
    private BaseRoute tmap(double fromLat, double fromLng, double toLat, double toLng, Truck truck) throws Exception {
        if (tmapAppKey == null || tmapAppKey.isBlank()) {
            throw new IllegalStateException("scargo.route.tmap.app-key 가 비어 있음");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startX", String.valueOf(fromLng));
        body.put("startY", String.valueOf(fromLat));
        body.put("endX", String.valueOf(toLng));
        body.put("endY", String.valueOf(toLat));
        body.put("reqCoordType", "WGS84GEO");
        body.put("resCoordType", "WGS84GEO");
        body.put("searchOption", "0");
        if (truck != null) {
            if (truck.heightM() != null) body.put("truckHeight", String.valueOf(truck.heightM().multiply(BigDecimal.valueOf(100)).intValue()));
            if (truck.grossWeightKg() != null) {
                body.put("truckWeight", String.valueOf(truck.grossWeightKg()));
                body.put("truckTotalWeight", String.valueOf(truck.grossWeightKg()));
            }
            body.put("truckWidth", String.valueOf(Math.round(truckWidthM * 100)));
            body.put("truckLength", String.valueOf(Math.round(truckLengthM * 100)));
        }

        String json = client(null, 15000).post()
                .uri(tmapUrl)
                .header("appKey", tmapAppKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        JsonNode features = objectMapper.readTree(json).path("features");
        if (!features.isArray() || features.isEmpty()) return null;

        List<double[]> path = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        double distance = 0, duration = 0;
        for (JsonNode f : features) {
            JsonNode g = f.path("geometry");
            JsonNode p = f.path("properties");
            String gtype = g.path("type").asText();
            if ("Point".equals(gtype)) {
                if (p.has("totalDistance")) distance = p.path("totalDistance").asDouble();
                if (p.has("totalTime")) duration = p.path("totalTime").asDouble();
                String type = tmapTurnType(p.path("turnType").asInt(-1));
                if (type == null) continue;
                JsonNode c = g.path("coordinates");
                steps.add(new Step(c.get(1).asDouble(), c.get(0).asDouble(), type,
                        textOrNull(p.path("name")), textOrNull(p.path("description"))));
            } else if ("LineString".equals(gtype)) {
                for (JsonNode c : g.path("coordinates")) {
                    double[] pt = {c.get(1).asDouble(), c.get(0).asDouble()};
                    if (path.isEmpty() || !same(path.get(path.size() - 1), pt)) path.add(pt);
                }
            }
        }
        if (path.size() < 2) return null;
        if (distance == 0) distance = GeoUtils.cumulative(path)[path.size() - 1];
        return new BaseRoute("tmap", distance, duration, path, steps);
    }

    /** TMAP turnType → 공통 코드. 직진(11)·보행자용 코드 등 안내가 필요 없는 지점은 null */
    private static String tmapTurnType(int t) {
        if (t == 200) return "DEPART";
        if (t == 201) return "ARRIVE";
        if (t == 12) return "LEFT";
        if (t == 13) return "RIGHT";
        if (t == 14) return "UTURN";
        if (t == 16) return "SHARP_LEFT";      // 8시 방향 좌회전
        if (t == 17) return "SLIGHT_LEFT";     // 10시 방향 좌회전
        if (t == 18) return "SLIGHT_RIGHT";    // 2시 방향 우회전
        if (t == 19) return "SHARP_RIGHT";     // 4시 방향 우회전
        if (t == 101 || t == 111) return "RAMP_RIGHT";  // 오른쪽 (도시)고속도로 입구
        if (t == 102 || t == 112) return "RAMP_LEFT";
        if (t == 103 || t == 113) return "STRAIGHT";
        if (t == 104 || t == 114) return "EXIT_RIGHT";  // 오른쪽 출구
        if (t == 105 || t == 115) return "EXIT_LEFT";
        if (t == 106 || t == 116) return "STRAIGHT";
        if (t == 117) return "KEEP_RIGHT";
        if (t == 118) return "KEEP_LEFT";
        if (t >= 131 && t <= 142) return "ROUNDABOUT";
        if (t == 249) return "TOLL";
        return null;
    }

    // ------------------------------------------------------------------ Valhalla

    /**
     * 직접 띄운 Valhalla 서버 (docker: ghcr.io/valhalla/valhalla-scripted + south-korea-latest.osm.pbf)
     * costing=truck 에 높이·무게를 넘기면 OSM 의 maxheight·maxweight 태그가 있는 도로를 피한다.
     */
    private BaseRoute valhalla(double fromLat, double fromLng, double toLat, double toLng, Truck truck) throws Exception {
        Map<String, Object> truckOpt = new LinkedHashMap<>();
        if (truck != null && truck.heightM() != null) truckOpt.put("height", truck.heightM().doubleValue());
        if (truck != null && truck.grossWeightKg() != null) truckOpt.put("weight", truck.grossWeightKg() / 1000.0);
        truckOpt.put("width", truckWidthM);
        truckOpt.put("length", truckLengthM);
        if (truck != null && truck.axleCount() != null) truckOpt.put("axle_count", truck.axleCount());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locations", List.of(Map.of("lat", fromLat, "lon", fromLng), Map.of("lat", toLat, "lon", toLng)));
        body.put("costing", "truck");
        body.put("costing_options", Map.of("truck", truckOpt));
        body.put("units", "kilometers");
        body.put("directions_type", "maneuvers");

        String json = client(valhallaUrl, 20000).post()
                .uri("/route")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        JsonNode trip = objectMapper.readTree(json).path("trip");
        JsonNode leg = trip.path("legs").path(0);
        List<double[]> path = decodePolyline(leg.path("shape").asText(), 1e6);
        if (path.size() < 2) return null;

        List<Step> steps = new ArrayList<>();
        for (JsonNode m : leg.path("maneuvers")) {
            String type = valhallaType(m.path("type").asInt(-1));
            if (type == null) continue;
            int idx = Math.min(path.size() - 1, Math.max(0, m.path("begin_shape_index").asInt(0)));
            JsonNode names = m.path("street_names");
            String road = names.isArray() && !names.isEmpty() ? names.get(0).asText() : null;
            steps.add(new Step(path.get(idx)[0], path.get(idx)[1], type, road, null));
        }
        return new BaseRoute("valhalla", trip.path("summary").path("length").asDouble() * 1000,
                trip.path("summary").path("time").asDouble(), path, steps);
    }

    private static String valhallaType(int t) {
        return switch (t) {
            case 1, 2, 3 -> "DEPART";
            case 4, 5, 6 -> "ARRIVE";
            case 9 -> "SLIGHT_RIGHT";
            case 10 -> "RIGHT";
            case 11 -> "SHARP_RIGHT";
            case 12, 13 -> "UTURN";
            case 14 -> "SHARP_LEFT";
            case 15 -> "LEFT";
            case 16 -> "SLIGHT_LEFT";
            case 18 -> "RAMP_RIGHT";
            case 19 -> "RAMP_LEFT";
            case 20 -> "EXIT_RIGHT";
            case 21 -> "EXIT_LEFT";
            case 23 -> "KEEP_RIGHT";
            case 24 -> "KEEP_LEFT";
            case 25, 37, 38 -> "MERGE";
            case 26 -> "ROUNDABOUT";
            default -> null; // 계속 직진 등은 안내하지 않음
        };
    }

    // ------------------------------------------------------------------ OSRM

    private BaseRoute osrm(double fromLat, double fromLng, double toLat, double toLng) throws Exception {
        String body = client(osrmUrl, 8000).get()
                .uri("/route/v1/driving/{a},{b};{c},{d}?overview=full&geometries=geojson&steps=true",
                        fromLng, fromLat, toLng, toLat)
                .retrieve()
                .body(String.class);

        JsonNode route = objectMapper.readTree(body).path("routes").path(0);
        JsonNode coords = route.path("geometry").path("coordinates");
        if (!coords.isArray() || coords.isEmpty()) return null;
        List<double[]> path = new ArrayList<>();
        for (JsonNode c : coords) path.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()});

        List<Step> steps = new ArrayList<>();
        for (JsonNode s : route.path("legs").path(0).path("steps")) {
            JsonNode m = s.path("maneuver");
            String type = osrmType(m.path("type").asText(), m.path("modifier").asText(""));
            if (type == null) continue;
            JsonNode loc = m.path("location");
            String road = s.path("name").asText("");
            steps.add(new Step(loc.get(1).asDouble(), loc.get(0).asDouble(), type, road.isBlank() ? null : road, null));
        }
        return new BaseRoute("osrm", route.path("distance").asDouble(),
                route.path("duration").asDouble() * truckTimeFactor, path, steps);
    }

    private static String osrmType(String type, String modifier) {
        switch (type) {
            case "depart": return "DEPART";
            case "arrive": return "ARRIVE";
            case "roundabout": case "rotary": case "roundabout turn": return "ROUNDABOUT";
            case "merge": return "MERGE";
            case "on ramp": return modifier.contains("left") ? "RAMP_LEFT" : "RAMP_RIGHT";
            case "off ramp": return modifier.contains("left") ? "EXIT_LEFT" : "EXIT_RIGHT";
            case "fork": return modifier.contains("left") ? "KEEP_LEFT" : "KEEP_RIGHT";
            case "new name": case "notification": return null;
            default: break; // turn, end of road, continue
        }
        return switch (modifier) {
            case "left" -> "LEFT";
            case "right" -> "RIGHT";
            case "slight left" -> "SLIGHT_LEFT";
            case "slight right" -> "SLIGHT_RIGHT";
            case "sharp left" -> "SHARP_LEFT";
            case "sharp right" -> "SHARP_RIGHT";
            case "uturn" -> "UTURN";
            default -> null; // straight
        };
    }

    // ------------------------------------------------------------------ 직선

    private BaseRoute straight(double fromLat, double fromLng, double toLat, double toLng) {
        int n = 60;
        List<double[]> path = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            path.add(new double[]{fromLat + (toLat - fromLat) * t, fromLng + (toLng - fromLng) * t});
        }
        double distance = GeoUtils.distanceM(fromLat, fromLng, toLat, toLng) * 1.25; // 도로 우회 보정
        double duration = distance / (straightSpeedKmh * 1000 / 3600);
        List<Step> steps = List.of(new Step(fromLat, fromLng, "DEPART", null, null),
                new Step(toLat, toLng, "ARRIVE", null, null));
        return new BaseRoute("straight", distance, duration, path, steps);
    }

    // ------------------------------------------------------------------ 공통

    /** Google 인코딩 폴리라인 디코딩 (Valhalla 는 정밀도 1e6) */
    static List<double[]> decodePolyline(String encoded, double precision) {
        List<double[]> out = new ArrayList<>();
        int index = 0, lat = 0, lng = 0;
        while (encoded != null && index < encoded.length()) {
            int[] r = next(encoded, index);
            lat += r[0];
            index = r[1];
            r = next(encoded, index);
            lng += r[0];
            index = r[1];
            out.add(new double[]{lat / precision, lng / precision});
        }
        return out;
    }

    private static int[] next(String s, int index) {
        int result = 0, shift = 0, b;
        do {
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < s.length());
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{value, index};
    }

    private static boolean same(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7;
    }

    private static String textOrNull(JsonNode n) {
        String t = n == null || n.isMissingNode() || n.isNull() ? null : n.asText();
        return t == null || t.isBlank() ? null : t;
    }
}
