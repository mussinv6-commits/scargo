package com.scargo.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.app.dto.AppDtos.RoutePoi;
import com.scargo.app.dto.AppDtos.Instruction;
import com.scargo.app.dto.AppDtos.RouteResponse;
import com.scargo.app.entity.HeightRestriction;
import com.scargo.app.entity.Hub;
import com.scargo.app.entity.OverloadCheckpoint;
import com.scargo.app.entity.Terminal;
import com.scargo.app.entity.TollOffice;
import com.scargo.app.repository.HeightRestrictionRepository;
import com.scargo.app.repository.OverloadCheckpointRepository;
import com.scargo.app.repository.TollOfficeRepository;
import com.scargo.app.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 26.10.03 2차: 경로 계산 + 턴바이턴 안내 + 경로 위 영업소·과적검문소·높이제한 찾기.
 *
 * scargo.route.provider 순서대로 시도 (예: "tmap,osrm,straight"). 실패하면 다음 엔진, 마지막은 항상 straight.
 * 엔진별 설명은 RouteProviders 참고. 경로는 운행 시작·경로 이탈 재탐색 때만 계산하고 DB 에 저장하므로
 * 위치 전송(5초마다)은 외부 API 를 부르지 않는다 → TMAP 무료 한도를 아끼는 구조.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final int MAX_PATH_POINTS = 2000;
    private static final double TOLL_NEAR_M = 500;
    private static final double CHECKPOINT_NEAR_M = 500;
    private static final double ROAD_HEIGHT_NEAR_M = 150;
    public static final BigDecimal HEIGHT_MARGIN_M = new BigDecimal("0.10"); // 높이 여유 10cm

    private final TollOfficeRepository tollOfficeRepository;
    private final OverloadCheckpointRepository overloadCheckpointRepository;
    private final HeightRestrictionRepository heightRestrictionRepository;
    private final ObjectMapper objectMapper;

    private final RouteProviders providers;

    @Value("${scargo.route.provider:osrm}")
    private String providerChain;

    /** 출발 좌표 → HUB 경로 + 턴바이턴 안내 + 경로 위 관심지점 */
    public RouteResponse plan(double fromLat, double fromLng, Hub hub, Terminal origin,
                              BigDecimal loadHeightM, Integer grossWeightKg, Integer axleCount) {
        double toLat = hub.getLatitude().doubleValue();
        double toLng = hub.getLongitude().doubleValue();

        RouteProviders.Truck truck = new RouteProviders.Truck(loadHeightM, grossWeightKg, axleCount);
        RouteProviders.BaseRoute base = null;
        List<String> chain = new ArrayList<>();
        for (String p : providerChain.split(",")) {
            if (!p.isBlank() && !chain.contains(p.trim().toLowerCase())) chain.add(p.trim().toLowerCase());
        }
        if (!chain.contains("straight")) chain.add("straight");
        for (String p : chain) {
            base = providers.route(p, fromLat, fromLng, toLat, toLng, truck);
            if (base != null) break;
        }

        List<double[]> path = simplify(base.path());
        double[] cum = GeoUtils.cumulative(path);
        double[] box = boundingBox(path, 0.02);

        List<RoutePoi> tolls = new ArrayList<>();
        for (TollOffice t : tollOfficeRepository.findByLatitudeBetweenAndLongitudeBetween(
                GeoUtils.bd(box[0]), GeoUtils.bd(box[1]), GeoUtils.bd(box[2]), GeoUtils.bd(box[3]))) {
            GeoUtils.Projection p = GeoUtils.project(path, cum, t.getLatitude().doubleValue(), t.getLongitude().doubleValue());
            if (p.distanceFromRouteM() <= TOLL_NEAR_M) {
                tolls.add(new RoutePoi("TOLL", t.getOfficeCode(), t.getOfficeName(),
                        t.getLatitude().doubleValue(), t.getLongitude().doubleValue(), p.alongM(), null, false));
            }
        }

        List<RoutePoi> checkpoints = new ArrayList<>();
        for (OverloadCheckpoint c : overloadCheckpointRepository.findByLatitudeBetweenAndLongitudeBetween(
                GeoUtils.bd(box[0]), GeoUtils.bd(box[1]), GeoUtils.bd(box[2]), GeoUtils.bd(box[3]))) {
            GeoUtils.Projection p = GeoUtils.project(path, cum, c.getLatitude().doubleValue(), c.getLongitude().doubleValue());
            if (p.distanceFromRouteM() <= CHECKPOINT_NEAR_M) {
                checkpoints.add(new RoutePoi("CHECKPOINT", String.valueOf(c.getCheckpointId()), c.getCheckpointName(),
                        c.getLatitude().doubleValue(), c.getLongitude().doubleValue(), p.alongM(), null, false));
            }
        }

        List<RoutePoi> heights = new ArrayList<>();
        // 출발 터미널 게이트
        if (origin != null) {
            for (HeightRestriction h : heightRestrictionRepository.findByTargetTypeAndTerminalId("TERMINAL_GATE", origin.getTerminalId())) {
                heights.add(heightPoi(h, 0, loadHeightM, path.get(0)));
            }
        }
        // 도로 시설물
        for (HeightRestriction h : heightRestrictionRepository.findByTargetTypeAndLatitudeBetweenAndLongitudeBetween(
                "ROAD", GeoUtils.bd(box[0]), GeoUtils.bd(box[1]), GeoUtils.bd(box[2]), GeoUtils.bd(box[3]))) {
            GeoUtils.Projection p = GeoUtils.project(path, cum, h.getLatitude().doubleValue(), h.getLongitude().doubleValue());
            if (p.distanceFromRouteM() <= ROAD_HEIGHT_NEAR_M) {
                heights.add(heightPoi(h, p.alongM(), loadHeightM, null));
            }
        }
        // 목적지 HUB 진입로
        for (HeightRestriction h : heightRestrictionRepository.findByTargetTypeAndHubId("HUB_ENTRANCE", hub.getHubId())) {
            heights.add(heightPoi(h, cum[cum.length - 1], loadHeightM, path.get(path.size() - 1)));
        }

        Comparator<RoutePoi> byAlong = Comparator.comparingDouble(RoutePoi::alongM);
        tolls.sort(byAlong);
        checkpoints.sort(byAlong);
        heights.sort(byAlong);

        // 턴바이턴 안내: 엔진이 준 안내 지점을 경로 위로 투영해서 "출발부터 몇 m 지점"인지 계산
        List<Instruction> instructions = new ArrayList<>();
        for (RouteProviders.Step st : base.steps()) {
            double along = "ARRIVE".equals(st.type()) ? cum[cum.length - 1]
                    : "DEPART".equals(st.type()) ? 0 : GeoUtils.project(path, cum, st.lat(), st.lng()).alongM();
            String text = st.text() != null ? st.text() : koreanText(st.type(), st.roadName(), hub.getBusinessName());
            instructions.add(new Instruction(along, st.lat(), st.lng(), st.type(), text, st.roadName()));
        }
        instructions.sort(Comparator.comparingDouble(Instruction::alongM));

        return new RouteResponse(base.provider(), base.distanceM(), base.durationS(), path, tolls, checkpoints, heights,
                instructions, java.time.OffsetDateTime.now().toString());
    }

    /** 공통 안내 코드 → 한글 안내문 (TMAP 은 자체 한글 안내문을 그대로 사용) */
    static String koreanText(String type, String road, String hubName) {
        String action = switch (type) {
            case "DEPART" -> "경로 안내를 시작합니다";
            case "ARRIVE" -> hubName == null ? "목적지에 도착했습니다" : "목적지 " + hubName + "에 도착했습니다";
            case "LEFT" -> "좌회전";
            case "RIGHT" -> "우회전";
            case "SLIGHT_LEFT" -> "왼쪽 방향";
            case "SLIGHT_RIGHT" -> "오른쪽 방향";
            case "SHARP_LEFT" -> "급좌회전";
            case "SHARP_RIGHT" -> "급우회전";
            case "UTURN" -> "유턴";
            case "RAMP_LEFT" -> "왼쪽 진입로로 진입";
            case "RAMP_RIGHT" -> "오른쪽 진입로로 진입";
            case "EXIT_LEFT" -> "왼쪽 출구로 나가세요";
            case "EXIT_RIGHT" -> "오른쪽 출구로 나가세요";
            case "KEEP_LEFT" -> "왼쪽 길로 가세요";
            case "KEEP_RIGHT" -> "오른쪽 길로 가세요";
            case "MERGE" -> "합류";
            case "ROUNDABOUT" -> "회전교차로 진입";
            case "TOLL" -> "톨게이트";
            default -> "직진";
        };
        if (road == null || road.isBlank() || "DEPART".equals(type) || "ARRIVE".equals(type)) return action;
        return road + " 방면 " + action;
    }

    private RoutePoi heightPoi(HeightRestriction h, double alongM, BigDecimal loadHeightM, double[] fallbackPoint) {
        double lat = h.getLatitude() != null ? h.getLatitude().doubleValue() : fallbackPoint[0];
        double lng = h.getLongitude() != null ? h.getLongitude().doubleValue() : fallbackPoint[1];
        boolean hazard = loadHeightM != null && h.getLimitHeightM().compareTo(loadHeightM.add(HEIGHT_MARGIN_M)) < 0;
        return new RoutePoi("HEIGHT_" + h.getTargetType(), String.valueOf(h.getRestrictionId()), h.getRestrictionName(),
                lat, lng, alongM, h.getLimitHeightM(), hazard);
    }

    public String toJson(RouteResponse route) {
        try {
            return objectMapper.writeValueAsString(route);
        } catch (Exception e) {
            throw new IllegalStateException("경로 저장 실패: " + e.getMessage());
        }
    }

    public RouteResponse fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, RouteResponse.class);
        } catch (Exception e) {
            log.warn("planned_route 읽기 실패: {}", e.getMessage());
            return null;
        }
    }

    private static List<double[]> simplify(List<double[]> path) {
        if (path.size() <= MAX_PATH_POINTS) return path;
        int step = (int) Math.ceil(path.size() / (double) MAX_PATH_POINTS);
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i += step) out.add(path.get(i));
        out.add(path.get(path.size() - 1));
        return out;
    }

    /** [minLat, maxLat, minLng, maxLng] */
    private static double[] boundingBox(List<double[]> path, double pad) {
        double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
        for (double[] p : path) {
            minLat = Math.min(minLat, p[0]);
            maxLat = Math.max(maxLat, p[0]);
            minLng = Math.min(minLng, p[1]);
            maxLng = Math.max(maxLng, p[1]);
        }
        return new double[]{minLat - pad, maxLat + pad, minLng - pad, maxLng + pad};
    }
}
