package com.scargo.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.app.util.GeoUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * fix_1500: 지정 경로(provider "fixed"). 사용자 결정 "경로는 HTML 의 기본 경로로 간다" 반영.
 *  - classpath:routes/*.json (HTML 지도 'SCARGO · 상세 경로 지도' 의 기본 경로 polyline 좌표 그대로)
 *  - 출발이 경로 시작점 1.5km 안(또는 경로 위 1km 안 = 경로 이탈 재탐색)이고 도착이 경로 끝점 1km 안이면 이 경로를 쓴다.
 *  - 그 밖의 출발·도착은 null → RouteService 가 다음 엔진(tmap → osrm → straight)으로 넘어간다.
 *  - 회전 안내(턴바이턴)는 원본에 없으므로 출발·도착 안내만 만든다. 영업소·과적검문소·높이제한·도착 음성은 RouteService/앱이 경로 위 지점으로 그대로 안내.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FixedRoutes {
    static final double START_NEAR_M = 1500, END_NEAR_M = 1000, ON_PATH_M = 1000;

    public record FixedRoute(String id, String name, double avgSpeedKmh, List<double[]> path) {}

    private final ObjectMapper objectMapper;
    private final List<FixedRoute> routes = new ArrayList<>();

    @PostConstruct
    void load() {
        try {
            for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:routes/*.json")) {
                try (InputStream in = r.getInputStream()) {
                    JsonNode n = objectMapper.readTree(in);
                    List<double[]> path = new ArrayList<>();
                    for (JsonNode c : n.path("path")) path.add(new double[]{c.get(0).asDouble(), c.get(1).asDouble()});
                    if (path.size() < 2) continue;
                    routes.add(new FixedRoute(n.path("id").asText(), n.path("name").asText(), n.path("avgSpeedKmh").asDouble(60), path));
                    log.info("[fixed] 지정 경로 {} ({}점)", n.path("id").asText(), path.size());
                }
            }
        } catch (Exception e) {
            log.warn("[fixed] 지정 경로 읽기 실패: {}", e.getMessage());
        }
    }

    public List<FixedRoute> all() { return routes; }

    /** 출발·도착에 맞는 지정 경로 (없으면 null) */
    public RouteProviders.BaseRoute route(double fromLat, double fromLng, double toLat, double toLng) {
        for (FixedRoute f : routes) {
            List<double[]> p = f.path();
            double[] end = p.get(p.size() - 1);
            if (GeoUtils.distanceM(toLat, toLng, end[0], end[1]) > END_NEAR_M) continue;
            int start;
            double[] s0 = p.get(0);
            if (GeoUtils.distanceM(fromLat, fromLng, s0[0], s0[1]) <= START_NEAR_M) start = 0;
            else { // 경로 이탈 재탐색 등: 현재 위치에서 가장 가까운 경로 지점부터
                int best = -1; double bd = Double.MAX_VALUE;
                for (int i = 0; i < p.size(); i++) { double d = GeoUtils.distanceM(fromLat, fromLng, p.get(i)[0], p.get(i)[1]); if (d < bd) { bd = d; best = i; } }
                if (bd > ON_PATH_M) continue;
                start = best;
            }
            List<double[]> path = new ArrayList<>();
            double[] first = p.get(start);
            if (GeoUtils.distanceM(fromLat, fromLng, first[0], first[1]) > 20) path.add(new double[]{fromLat, fromLng});
            path.addAll(p.subList(start, p.size()));
            if (GeoUtils.distanceM(toLat, toLng, end[0], end[1]) > 20) path.add(new double[]{toLat, toLng});
            double[] cum = GeoUtils.cumulative(path);
            double dist = cum[cum.length - 1];
            double dur = dist / (f.avgSpeedKmh() * 1000 / 3600);
            double[] a = path.get(0), b = path.get(path.size() - 1);
            List<RouteProviders.Step> steps = List.of(
                    new RouteProviders.Step(a[0], a[1], "DEPART", null, f.name() + "로 안내를 시작합니다"),
                    new RouteProviders.Step(b[0], b[1], "ARRIVE", null, null));
            return new RouteProviders.BaseRoute("fixed", dist, dur, path, steps);
        }
        return null;
    }
}
