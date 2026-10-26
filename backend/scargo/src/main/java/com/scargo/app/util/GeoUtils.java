package com.scargo.app.util;

import java.math.BigDecimal;
import java.util.List;

/** 26.10.03 2차: 좌표 계산 (PostGIS 없이 하버사인으로 처리) */
public final class GeoUtils {

    private static final double R = 6_371_000d; // 지구 반지름(m)

    private GeoUtils() {
    }

    public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    public static double distanceM(BigDecimal lat1, BigDecimal lng1, double lat2, double lng2) {
        return distanceM(lat1.doubleValue(), lng1.doubleValue(), lat2, lng2);
    }

    /** 위도 기준 반경(m)을 위도 차이(도)로 */
    public static double latDelta(double meters) {
        return meters / 111_320d;
    }

    /** 경도 기준 반경(m)을 경도 차이(도)로 (해당 위도에서) */
    public static double lngDelta(double meters, double atLat) {
        return meters / (111_320d * Math.cos(Math.toRadians(atLat)));
    }

    public static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    /**
     * 점 P 와 선분 AB 사이 최단거리(m). 짧은 구간이므로 평면 근사(등장방형 투영) 사용.
     * 결과 [0] = 거리(m), [1] = 선분 위 위치 비율 t (0~1)
     */
    public static double[] pointToSegment(double pLat, double pLng, double aLat, double aLng, double bLat, double bLng) {
        double k = Math.cos(Math.toRadians(pLat));
        double ax = aLng * k, ay = aLat, bx = bLng * k, by = bLat, px = pLng * k, py = pLat;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        double cLat = aLat + (bLat - aLat) * t;
        double cLng = aLng + (bLng - aLng) * t;
        return new double[]{distanceM(pLat, pLng, cLat, cLng), t};
    }

    /** 경로(위도,경도 목록)의 누적 거리(m) 배열 */
    public static double[] cumulative(List<double[]> path) {
        double[] cum = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1), b = path.get(i);
            cum[i] = cum[i - 1] + distanceM(a[0], a[1], b[0], b[1]);
        }
        return cum;
    }

    /**
     * 경로 위로 투영한 결과.
     * distanceFromRouteM = 경로까지의 거리, alongM = 출발점부터 경로를 따라간 거리
     */
    public record Projection(double distanceFromRouteM, double alongM, int segmentIndex) {
    }

    public static Projection project(List<double[]> path, double[] cum, double lat, double lng) {
        if (path.size() == 1) {
            return new Projection(distanceM(lat, lng, path.get(0)[0], path.get(0)[1]), 0, 0);
        }
        double best = Double.MAX_VALUE, along = 0;
        int seg = 0;
        for (int i = 1; i < path.size(); i++) {
            double[] a = path.get(i - 1), b = path.get(i);
            // 빠른 제외: 두 끝점 모두 5km 이상 떨어진 위도 차이면 건너뜀
            if (Math.abs(a[0] - lat) > 0.05 && Math.abs(b[0] - lat) > 0.05
                    && Math.signum(a[0] - lat) == Math.signum(b[0] - lat)) {
                continue;
            }
            double[] r = pointToSegment(lat, lng, a[0], a[1], b[0], b[1]);
            if (r[0] < best) {
                best = r[0];
                along = cum[i - 1] + (cum[i] - cum[i - 1]) * r[1];
                seg = i - 1;
            }
        }
        if (best == Double.MAX_VALUE) { // 전부 건너뛴 경우 (경로에서 아주 멀리 있음)
            double nearest = Double.MAX_VALUE;
            for (int i = 0; i < path.size(); i++) {
                double d = distanceM(lat, lng, path.get(i)[0], path.get(i)[1]);
                if (d < nearest) {
                    nearest = d;
                    along = cum[i];
                    seg = Math.max(0, i - 1);
                }
            }
            best = nearest;
        }
        return new Projection(best, along, seg);
    }
}
