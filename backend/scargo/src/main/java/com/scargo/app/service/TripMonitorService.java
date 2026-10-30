package com.scargo.app.service;

import com.scargo.Enum.NotificationType;
import com.scargo.app.dto.AppDtos.*;
import com.scargo.app.entity.Hub;
import com.scargo.app.entity.OrderStatus;
import com.scargo.app.entity.Terminal;
import com.scargo.app.entity.TransportOrder;
import com.scargo.app.entity.TripLocation;
import com.scargo.app.repository.HubRepository;
import com.scargo.app.repository.TerminalRepository;
import com.scargo.app.repository.TransportOrderRepository;
import com.scargo.app.repository.TripEventRepository;
import com.scargo.app.repository.TripLocationRepository;
import com.scargo.app.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.10.03 2차: [05 내비게이션] 운행 중 위치 수신 → 남은 거리/ETA, 경고, 영업소 통과, HUB 도착 판정.
 * 앱은 운행 중 5~10초마다 POST /api/app/orders/{id}/locations 를 보낸다.
 */
@Service
@RequiredArgsConstructor
public class TripMonitorService {

    private final TransportOrderService orderService;
    private final TransportOrderRepository orderRepository;
    private final TripLocationRepository tripLocationRepository;
    private final TripEventRepository tripEventRepository;
    private final HubRepository hubRepository;
    private final TerminalRepository terminalRepository;
    private final RouteService routeService;

    @Value("${scargo.app.off-route-m:1000}")
    private double offRouteM;

    @Value("${scargo.app.checkpoint-alert-m:3000}")
    private double checkpointAlertM;

    @Value("${scargo.app.height-alert-m:5000}")
    private double heightAlertM;

    @Transactional
    public TripStatus ingest(Long accountId, Long orderId, LocationRequest req) {
        if (req == null || req.lat() == null || req.lng() == null) {
            throw new IllegalArgumentException("위치(lat, lng)가 필요합니다.");
        }
        TransportOrder order = orderService.myOrder(accountId, orderId);
        double lat = req.lat(), lng = req.lng();

        if (order.is(OrderStatus.IN_TRANSIT) || order.is(OrderStatus.ARRIVED)) {
            TripLocation loc = new TripLocation();
            loc.setOrderId(orderId);
            loc.setLatitude(BigDecimal.valueOf(lat));
            loc.setLongitude(BigDecimal.valueOf(lng));
            loc.setSpeedKmh(req.speedKmh() == null ? null : BigDecimal.valueOf(req.speedKmh()));
            loc.setHeading(req.heading() == null ? null : req.heading().shortValue());
            loc.setAccuracyM(req.accuracyM() == null ? null : BigDecimal.valueOf(req.accuracyM()));
            loc.setRecordedAt(req.recordedAt() != null ? req.recordedAt() : OffsetDateTime.now());
            tripLocationRepository.save(loc);
        }
        return evaluate(order, lat, lng, true);
    }

    /** 위치를 저장하지 않고 현재 상태만 계산 (화면 새로고침용) */
    @Transactional(readOnly = true)
    public TripStatus status(Long accountId, Long orderId) {
        TransportOrder order = orderService.myOrder(accountId, orderId);
        double[] p = tripLocationRepository.findFirstByOrderIdOrderByRecordedAtDesc(orderId)
                .map(l -> new double[]{l.getLatitude().doubleValue(), l.getLongitude().doubleValue()})
                .orElseGet(() -> {
                    Terminal t = terminalRepository.findById(order.getOriginTerminalId()).orElse(null);
                    return t == null || t.getLatitude() == null ? null
                            : new double[]{t.getLatitude().doubleValue(), t.getLongitude().doubleValue()};
                });
        if (p == null) {
            return new TripStatus(order.getStatus(), order.is(OrderStatus.ARRIVED), 0, 0, 0, order.getEta(), 0, false, List.of(), List.of());
        }
        return evaluate(order, p[0], p[1], false);
    }

    private TripStatus evaluate(TransportOrder order, double lat, double lng, boolean record) {
        RouteResponse route = routeService.fromJson(order.getPlannedRoute());
        Hub hub = hubRepository.findById(order.getDestHubId()).orElseThrow();
        double toHub = GeoUtils.distanceM(hub.getLatitude(), hub.getLongitude(), lat, lng);

        if (route == null || route.path() == null || route.path().size() < 2) {
            boolean arrived = order.is(OrderStatus.ARRIVED);
            return new TripStatus(order.getStatus(), arrived, 0, toHub, 0, order.getEta(), 0, false, List.of(), List.of());
        }

        double[] cum = GeoUtils.cumulative(route.path());
        double total = cum[cum.length - 1];
        GeoUtils.Projection pr = GeoUtils.project(route.path(), cum, lat, lng);
        double along = pr.alongM();
        double remaining = Math.max(0, total - along) + (pr.distanceFromRouteM() > offRouteM ? pr.distanceFromRouteM() : 0);
        double remainingS = total <= 0 ? 0 : route.durationS() * Math.min(1, remaining / total);
        OffsetDateTime eta = OffsetDateTime.now().plusSeconds((long) remainingS);
        boolean offRoute = pr.distanceFromRouteM() > offRouteM;

        List<Alert> alerts = new ArrayList<>();
        boolean moving = order.is(OrderStatus.IN_TRANSIT);

        // 1) 경로 이탈 (10분에 한 번만 기록)
        if (offRoute && moving) {
            alerts.add(new Alert("ROUTE_DEVIATION", "WARN", "경로 이탈",
                    "계획 경로에서 " + Math.round(pr.distanceFromRouteM()) + "m 벗어났습니다.", null));
            if (record && !tripEventRepository.existsByOrderIdAndEventTypeAndOccurredAtAfter(
                    order.getOrderId(), "ROUTE_DEVIATION", OffsetDateTime.now().minusMinutes(10))) {
                orderService.event(order, "ROUTE_DEVIATION", null, null, lat, lng,
                        "경로에서 " + Math.round(pr.distanceFromRouteM()) + "m 이탈");
            }
        }

        // 2) 높이제한 (내 적재 높이보다 낮은 곳은 DANGER, 나머지는 가까울 때 INFO)
        for (RoutePoi h : route.heights()) {
            double ahead = h.alongM() - along;
            if (ahead < -100 || ahead > heightAlertM) continue;
            if (h.hazard()) {
                alerts.add(new Alert("HEIGHT", "DANGER", "전방 " + km(ahead) + " 높이제한 " + h.limitHeightM() + "m",
                        h.name() + " · 내 적재 높이 " + order.getLoadHeightM() + "m - 우회 필요", Math.max(0, ahead)));
                if (record && once(order, "HEIGHT_WARNING", h.id())) {
                    orderService.event(order, "HEIGHT_WARNING", "HEIGHT", h.id(), h.lat(), h.lng(), h.name() + " " + h.limitHeightM() + "m");
                    notifyDriver(order, "경로상 높이제한 구간", h.name() + " " + h.limitHeightM() + "m · 내 차량 " + order.getLoadHeightM() + "m. 우회 경로를 확인하세요.");
                }
            } else if (ahead <= 2000) {
                alerts.add(new Alert("HEIGHT", "INFO", "전방 " + km(ahead) + " 높이제한 " + h.limitHeightM() + "m",
                        h.name() + " · 통과 가능", Math.max(0, ahead)));
            }
        }

        // 3) 과적검문소
        for (RoutePoi c : route.checkpoints()) {
            double ahead = c.alongM() - along;
            if (ahead < 0 || ahead > checkpointAlertM) continue;
            alerts.add(new Alert("CHECKPOINT", "WARN", "전방 " + km(ahead) + " 과적검문소입니다",
                    c.name() + (order.getGrossWeightKg() == null ? "" : " · 예상 총중량 " + order.getGrossWeightKg() + "kg"), ahead));
            if (record && once(order, "CHECKPOINT_NEAR", c.id())) {
                orderService.event(order, "CHECKPOINT_NEAR", "CHECKPOINT", c.id(), c.lat(), c.lng(), c.name() + " 접근");
                notifyDriver(order, "과적검문소 접근", "전방 " + km(ahead) + " 과적검문소입니다. " + c.name());
            }
        }

        // 4) 영업소 통과
        List<TollProgress> tolls = new ArrayList<>();
        for (RoutePoi t : route.tolls()) {
            boolean passed = along >= t.alongM();
            tolls.add(new TollProgress(t.id(), t.name(), t.alongM(), passed));
            if (passed && record && moving && once(order, "TOLL_PASS", t.id())) {
                orderService.event(order, "TOLL_PASS", "TOLL_OFFICE", t.id(), t.lat(), t.lng(), t.name() + " 영업소 통과");
            }
        }

        // 5) HUB 도착 (지오펜스)
        int fence = hub.getGeofenceRadiusM() == null ? 300 : hub.getGeofenceRadiusM();
        if (moving && toHub <= fence && record) {
            orderService.markArrived(order, "GEOFENCE", lat, lng);
        }
        boolean arrived = order.is(OrderStatus.ARRIVED);
        if (arrived) {
            alerts.add(0, new Alert("ARRIVED", "INFO", "HUB 도착", hub.getBusinessName() + "에 도착했습니다. 인계를 진행하세요." /* 26.10.29 띄어쓰기 */, 0d));
        }

        alerts.sort((a, b) -> Integer.compare(levelRank(a.level()), levelRank(b.level())));
        return new TripStatus(order.getStatus(), arrived, along, arrived ? 0 : remaining, arrived ? 0 : remainingS,
                arrived ? order.getArrivedAt() : eta, pr.distanceFromRouteM(), offRoute, alerts, tolls);
    }

    private boolean once(TransportOrder order, String type, String refId) {
        return !tripEventRepository.existsByOrderIdAndEventTypeAndRefId(order.getOrderId(), type, refId);
    }

    private void notifyDriver(TransportOrder order, String title, String message) {
        if (order.getDriverAccountId() != null) {
            orderService.notifyDriver(order.getDriverAccountId(), order, NotificationType.SYSTEM, title, message);
        }
    }

    private static int levelRank(String level) {
        return switch (level) {
            case "DANGER" -> 0;
            case "WARN" -> 1;
            default -> 2;
        };
    }

    private static String km(double m) {
        if (m < 1000) return Math.max(0, Math.round(m / 10) * 10) + "m";
        return String.format("%.1fkm", m / 1000);
    }
}
