package com.scargo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 26.10.02 추가: 계중대 화면 지도(OpenStreetMap)에 그릴 차량 이동 경로.
 * 진입 게이트(origin) → 계중대(weighbridge) → [경유지(waypoints)] → 목적지(destination) 순서.
 * 좌표가 없는 지점은 null 로 내려가고, 화면은 있는 지점만 이어서 그린다.
 */
@Getter
@AllArgsConstructor
public class WeighbridgeRouteResponse {

    private final String vehicleNo;
    private final Point origin;
    private final Point weighbridge;
    private final List<Point> waypoints;
    private final Point destination;
    private final List<Point> otherGates; // 지도 배경에 흐리게 표시할 나머지 게이트들

    @Getter
    @AllArgsConstructor
    public static class Point {
        private final String code;
        private final String name;
        private final Double lat;
        private final Double lng;
    }
}
