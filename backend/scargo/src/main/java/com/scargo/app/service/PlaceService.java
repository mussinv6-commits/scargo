package com.scargo.app.service;

import com.scargo.app.dto.AppDtos.PlaceItem;
import com.scargo.app.entity.HeightRestriction;
import com.scargo.app.entity.Hub;
import com.scargo.app.repository.HeightRestrictionRepository;
import com.scargo.app.repository.HubRepository;
import com.scargo.app.repository.OverloadCheckpointRepository;
import com.scargo.app.repository.PrivateWeighbridgeRepository;
import com.scargo.app.repository.TerminalRepository;
import com.scargo.app.repository.TollOfficeRepository;
import com.scargo.app.entity.Terminal;
import com.scargo.app.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.10.03 2차: [09 정보 조회 지도] / [04 가까운 민간계량소]
 * type = HUB | WEIGHBRIDGE | CHECKPOINT | TOLL | TERMINAL
 * 상세(핀 선택): HUB | WEIGHBRIDGE | CHECKPOINT | TOLL | TERMINAL | HEIGHT
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceService {

    private final HubRepository hubRepository;
    private final PrivateWeighbridgeRepository weighbridgeRepository;
    private final OverloadCheckpointRepository checkpointRepository;
    private final TollOfficeRepository tollOfficeRepository;
    private final HeightRestrictionRepository heightRestrictionRepository;
    private final TerminalRepository terminalRepository;

    /** 법정 차량 높이 기준 (도로법 시행령 제79조: 4.0m, 도로관리청이 고시한 도로 4.2m) - 안내 문구용 */
    public static final String LEGAL_HEIGHT_NOTE = "도로 운행 기준 차량 높이 4.0m (고시된 도로 4.2m)";

    public List<PlaceItem> find(String type, Double lat, Double lng, Double radiusKm, String q, Integer limit) {
        String t = type == null ? "HUB" : type.toUpperCase();
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        boolean hasPoint = lat != null && lng != null;
        boolean hasQuery = q != null && !q.isBlank();
        if (!hasPoint && !hasQuery) {
            throw new IllegalArgumentException("lat/lng 또는 검색어(q) 중 하나는 필요합니다.");
        }
        double radiusM = (radiusKm == null ? 30 : Math.min(radiusKm, 300)) * 1000;

        List<PlaceItem> items = new ArrayList<>();
        BigDecimal[] box = hasPoint ? box(lat, lng, radiusM) : null;

        switch (t) {
            case "HUB" -> {
                List<Hub> hubs = hasQuery
                        ? hubRepository.search(q.trim(), PageRequest.of(0, max))
                        : hubRepository.findByIsActiveTrueAndLatitudeBetweenAndLongitudeBetween(box[0], box[1], box[2], box[3]);
                for (Hub h : hubs) {
                    items.add(hubItem(h, lat, lng));
                }
            }
            case "WEIGHBRIDGE" -> {
                var list = hasQuery
                        ? weighbridgeRepository.search(q.trim(), PageRequest.of(0, max))
                        : weighbridgeRepository.findByStatusAndLatitudeBetweenAndLongitudeBetween("영업/정상", box[0], box[1], box[2], box[3]);
                for (var w : list) {
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("phone", w.getPhone());
                    extra.put("status", w.getStatus());
                    items.add(item("WEIGHBRIDGE", String.valueOf(w.getWeighbridgeId()), w.getBusinessName(),
                            w.getRoadAddress() != null ? w.getRoadAddress() : w.getAddress(),
                            w.getLatitude(), w.getLongitude(), lat, lng, extra));
                }
            }
            case "CHECKPOINT" -> {
                var list = hasQuery
                        ? checkpointRepository.findActiveByNameOrAddress(q.trim())
                        : checkpointRepository.findByIsActiveTrueAndLatitudeBetweenAndLongitudeBetween(box[0], box[1], box[2], box[3]);
                for (var c : list) {
                    items.add(item("CHECKPOINT", String.valueOf(c.getCheckpointId()), c.getCheckpointName(),
                            c.getRoadAddress() != null ? c.getRoadAddress() : c.getAddress(),
                            c.getLatitude(), c.getLongitude(), lat, lng, Map.of()));
                }
            }
            case "TOLL" -> {
                var list = hasQuery
                        ? tollOfficeRepository.findByOfficeNameContainingOrRouteNameContaining(q.trim(), q.trim())
                        : tollOfficeRepository.findByLatitudeBetweenAndLongitudeBetween(box[0], box[1], box[2], box[3]);
                for (var o : list) {
                    Map<String, Object> extra = new HashMap<>();
                    extra.put("routeName", o.getRouteName());
                    items.add(item("TOLL", o.getOfficeCode(), o.getOfficeName() + " 영업소", o.getAddress(),
                            o.getLatitude(), o.getLongitude(), lat, lng, extra));
                }
            }
            case "TERMINAL" -> {
                for (Terminal tm : terminalRepository.findAll()) {
                    if (!Boolean.TRUE.equals(tm.getIsActive()) || tm.getLatitude() == null) continue;
                    if (hasQuery && !(contains(tm.getTerminalName(), q) || contains(tm.getAddress(), q) || contains(tm.getOperator(), q))) continue;
                    items.add(terminalItem(tm, lat, lng));
                }
            }
            default -> throw new IllegalArgumentException("type 은 HUB, WEIGHBRIDGE, CHECKPOINT, TOLL, TERMINAL 중 하나입니다.");
        }

        if (hasPoint) {
            items.removeIf(i -> i.distanceM() != null && i.distanceM() > radiusM && !hasQuery);
            items.sort(Comparator.comparingDouble(i -> i.distanceM() == null ? Double.MAX_VALUE : i.distanceM()));
        }
        return items.size() > max ? new ArrayList<>(items.subList(0, max)) : items;
    }

    /** 지도 핀을 눌렀을 때 보여줄 상세 정보 */
    public PlaceItem detail(String type, String id, Double lat, Double lng) {
        String t = type == null ? "" : type.toUpperCase();
        switch (t) {
            case "HUB" -> {
                return hubItem(hubRepository.findById(Long.valueOf(id)).orElseThrow(() -> notFound(t, id)), lat, lng);
            }
            case "TERMINAL" -> {
                return terminalItem(terminalRepository.findById(Long.valueOf(id)).orElseThrow(() -> notFound(t, id)), lat, lng);
            }
            case "WEIGHBRIDGE" -> {
                var w = weighbridgeRepository.findById(Long.valueOf(id)).orElseThrow(() -> notFound(t, id));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("phone", w.getPhone());
                extra.put("status", w.getStatus());
                extra.put("source", "전국 민간계량소 데이터");
                return item("WEIGHBRIDGE", id, w.getBusinessName(), w.getRoadAddress() != null ? w.getRoadAddress() : w.getAddress(),
                        w.getLatitude(), w.getLongitude(), lat, lng, extra);
            }
            case "CHECKPOINT" -> {
                var c = checkpointRepository.findById(Long.valueOf(id)).orElseThrow(() -> notFound(t, id));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("grossLimitKg", 40000);
                extra.put("axleLimitKg", 10000);
                extra.put("source", "전국 과적검문소 데이터");
                return item("CHECKPOINT", id, c.getCheckpointName(), c.getRoadAddress() != null ? c.getRoadAddress() : c.getAddress(),
                        c.getLatitude(), c.getLongitude(), lat, lng, extra);
            }
            case "TOLL" -> {
                var o = tollOfficeRepository.findById(id).orElseThrow(() -> notFound(t, id));
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("routeName", o.getRouteName());
                extra.put("source", "한국도로공사 영업소 위치정보");
                return item("TOLL", id, o.getOfficeName() + " 영업소", o.getAddress(), o.getLatitude(), o.getLongitude(), lat, lng, extra);
            }
            case "HEIGHT" -> {
                HeightRestriction h = heightRestrictionRepository.findById(Long.valueOf(id)).orElseThrow(() -> notFound(t, id));
                BigDecimal pLat = h.getLatitude(), pLng = h.getLongitude();
                String address = null;
                if ((pLat == null || pLng == null) && h.getHubId() != null) {
                    Hub hub = hubRepository.findById(h.getHubId()).orElse(null);
                    if (hub != null) { pLat = hub.getLatitude(); pLng = hub.getLongitude(); address = hub.getRoadAddress(); }
                }
                if ((pLat == null || pLng == null) && h.getTerminalId() != null) {
                    Terminal tm = terminalRepository.findById(h.getTerminalId()).orElse(null);
                    if (tm != null) { pLat = tm.getLatitude(); pLng = tm.getLongitude(); address = tm.getAddress(); }
                }
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("targetType", h.getTargetType());
                extra.put("limitHeightM", h.getLimitHeightM());
                extra.put("source", h.getSource());
                extra.put("verifiedAt", h.getVerifiedAt());
                extra.put("legalNote", LEGAL_HEIGHT_NOTE);
                return item("HEIGHT", id, h.getRestrictionName(), address,
                        pLat == null ? BigDecimal.ZERO : pLat, pLng == null ? BigDecimal.ZERO : pLng, lat, lng, extra);
            }
            default -> throw new IllegalArgumentException("알 수 없는 type: " + type);
        }
    }

    PlaceItem terminalItem(Terminal tm, Double lat, Double lng) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("code", tm.getTerminalCode());
        extra.put("portName", tm.getPortName());
        extra.put("operator", tm.getOperator());
        extra.put("coordVerified", tm.getCoordVerified());
        BigDecimal limit = heightRestrictionRepository.findByTargetTypeAndTerminalId("TERMINAL_GATE", tm.getTerminalId()).stream()
                .map(HeightRestriction::getLimitHeightM).min(BigDecimal::compareTo).orElse(null);
        extra.put("gateHeightLimitM", limit);
        extra.put("gateHeightNote", limit != null ? "운영사 확인 값" : (tm.getGateHeightNote() != null ? tm.getGateHeightNote() : "게이트 높이 미확인"));
        extra.put("legalNote", LEGAL_HEIGHT_NOTE);
        return item("TERMINAL", String.valueOf(tm.getTerminalId()), tm.getTerminalName(), tm.getAddress(),
                tm.getLatitude(), tm.getLongitude(), lat, lng, extra);
    }

    private static boolean contains(String text, String q) {
        return text != null && text.contains(q.trim());
    }

    private static IllegalArgumentException notFound(String type, String id) {
        return new IllegalArgumentException(type + " " + id + " 을(를) 찾을 수 없습니다.");
    }

    PlaceItem hubItem(Hub h, Double lat, Double lng) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("hubType", h.getHubType());
        extra.put("sizeClass", h.getSizeClass());
        extra.put("mega", h.getIsMega());
        extra.put("totalAreaM2", h.getTotalAreaM2());
        BigDecimal limit = heightRestrictionRepository.findByTargetTypeAndHubId("HUB_ENTRANCE", h.getHubId()).stream()
                .map(HeightRestriction::getLimitHeightM).min(BigDecimal::compareTo).orElse(null);
        extra.put("entranceHeightLimitM", limit);
        extra.put("geofenceRadiusM", h.getGeofenceRadiusM());
        extra.put("legalNote", LEGAL_HEIGHT_NOTE);
        return item("HUB", String.valueOf(h.getHubId()), h.getBusinessName(), h.getRoadAddress(),
                h.getLatitude(), h.getLongitude(), lat, lng, extra);
    }

    private static PlaceItem item(String type, String id, String name, String address, BigDecimal pLat, BigDecimal pLng,
                                  Double lat, Double lng, Map<String, Object> extra) {
        Double d = (lat == null || lng == null) ? null : GeoUtils.distanceM(pLat, pLng, lat, lng);
        return new PlaceItem(type, id, name, address, pLat.doubleValue(), pLng.doubleValue(), d, extra);
    }

    private static BigDecimal[] box(double lat, double lng, double radiusM) {
        double dLat = GeoUtils.latDelta(radiusM);
        double dLng = GeoUtils.lngDelta(radiusM, lat);
        return new BigDecimal[]{GeoUtils.bd(lat - dLat), GeoUtils.bd(lat + dLat), GeoUtils.bd(lng - dLng), GeoUtils.bd(lng + dLng)};
    }
}
