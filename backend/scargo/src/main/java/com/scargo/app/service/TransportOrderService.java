package com.scargo.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.Enum.NotificationType;
import com.scargo.app.dto.AppDtos.*;
import com.scargo.app.entity.*;
import com.scargo.app.repository.*;
import com.scargo.app.util.ContainerNumbers;
import com.scargo.app.util.GeoUtils;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.entity.Container;
import com.scargo.entity.Gate;
import com.scargo.entity.GateLog;
import com.scargo.entity.OverloadCheck;
import com.scargo.entity.Truck;
import com.scargo.repository.ContainerRepository;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.GateRepository;
import com.scargo.repository.NotificationRepository;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;
import com.scargo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 26.10.03 2차: 운송오더 흐름 (와이어프레임 02 홈 → 03 적재 → 04 점검 → 05 출발 → 06 인계 → 07 이력)
 *
 * 상태: ASSIGNED → LOADED(적재 등록) → GATE_OUT(OUT 게이트 OCR) → IN_TRANSIT(운행 시작) → ARRIVED → HANDED_OVER
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransportOrderService {
    private final com.scargo.ops.service.SealService sealService; // fix_1300: 인계 씰 대조

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BigDecimal DEFAULT_CHASSIS_HEIGHT_M = new BigDecimal("1.30");

    private final TransportOrderRepository orderRepository;
    private final TerminalRepository terminalRepository;
    private final HubRepository hubRepository;
    private final HeightRestrictionRepository heightRestrictionRepository;
    private final VehicleSpecRepository vehicleSpecRepository;
    private final TripEventRepository tripEventRepository;
    private final TripLocationRepository tripLocationRepository;
    private final HandoverRepository handoverRepository;
    private final ContainerRepository containerRepository;
    private final TruckRepository truckRepository;
    private final GateLogRepository gateLogRepository;
    private final GateRepository gateRepository;
    private final OverloadCheckRepository overloadCheckRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final RouteService routeService;
    private final PlaceService placeService;
    private final ObjectMapper objectMapper;

    /** 자기 자신의 프록시 - @Transactional 메서드를 내부에서 부를 때 사용 (this.xxx() 는 트랜잭션이 안 걸림) */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private TransportOrderService self;

    @Value("${scargo.weighbridge.gross-limit-kg:40000}")
    private int grossLimitKg;

    // fix_1300: 업로드 루트는 scargo.upload-root 하나 (UploadStorage). 인계 사진·서명은 그 아래 app/handover/{orderId}

    // =====================================================================
    // 02 홈
    // =====================================================================

    @Transactional(readOnly = true)
    public HomeResponse home(Long accountId) {
        List<TransportOrder> active = activeOrders(accountId);
        OrderDetail current = active.isEmpty() ? null : detail(active.get(0));
        List<OrderSummary> waiting = active.stream().skip(1).map(this::summary).toList();
        long unread = notificationRepository.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId).size();
        return new HomeResponse(current, waiting, unread);
    }

    /** 진행 중 오더: 이미 움직이고 있는 오더를 맨 앞으로, 나머지는 배차 순 */
    private List<TransportOrder> activeOrders(Long accountId) {
        Map<Long, TransportOrder> byId = new LinkedHashMap<>();
        orderRepository.findByDriverAccountIdAndStatusInOrderByAssignedAtAsc(accountId, OrderStatus.ACTIVE)
                .forEach(o -> byId.put(o.getOrderId(), o));
        // 26.10.27: 차량으로 찾은 오더는 '배차 기사가 비어 있는' 것만. 다른 기사(driver_account_id)에게 배차된 오더가
        //  같은 차량을 쓰는 기사의 홈·내비에도 보여서, 한 기사가 도착하면 다른 기사도 '도착'으로 보이던 문제
        myTruck(accountId).ifPresent(t -> orderRepository
                .findByVehicleNoAndStatusInOrderByAssignedAtAsc(t.getVehicleNo(), OrderStatus.ACTIVE).stream()
                .filter(o -> o.getDriverAccountId() == null)
                .forEach(o -> byId.putIfAbsent(o.getOrderId(), o)));
        List<String> priority = List.of("ARRIVED", "IN_TRANSIT", "GATE_OUT", "LOADED", "ASSIGNED", "CREATED");
        List<TransportOrder> list = new ArrayList<>(byId.values());
        list.sort(Comparator.<TransportOrder>comparingInt(o -> priority.indexOf(o.getStatus()))
                .thenComparing(o -> o.getAssignedAt() == null ? OffsetDateTime.MAX : o.getAssignedAt()));
        return list;
    }

    @Transactional(readOnly = true)
    public OrderDetail getDetail(Long accountId, Long orderId) {
        return detail(myOrder(accountId, orderId));
    }

    // =====================================================================
    // 03 컨테이너 적재 등록 - 차량번호 + 컨테이너번호 → 출발지·목적지 자동 입력
    // =====================================================================

    @Transactional
    public OrderDetail lookup(Long accountId, LookupRequest req) {
        String containerNo = ContainerNumbers.normalize(req.containerNo());
        String vehicleNo = req.vehicleNo() == null ? null : req.vehicleNo().replaceAll("\\s", "");

        if (containerNo == null || !containerNo.matches("^[A-Z]{4}[0-9]{7}$")) {
            throw new IllegalArgumentException("컨테이너 번호는 영문 4자리 + 숫자 7자리입니다. (예: SCGU1234565)");
        }
        if (!ContainerNumbers.isValid(containerNo)) {
            throw new IllegalArgumentException("컨테이너 번호의 마지막 체크디지트가 맞지 않습니다. 번호를 다시 확인해 주세요.");
        }
        Optional<Truck> truck = myTruck(accountId);
        if (vehicleNo == null || vehicleNo.isBlank()) {
            vehicleNo = truck.map(Truck::getVehicleNo)
                    .orElseThrow(() -> new IllegalArgumentException("배정된 차량이 없습니다. 운송사에 차량 배정을 요청하세요."));
        }
        if (truck.isPresent() && !truck.get().getVehicleNo().equals(vehicleNo)) {
            throw new IllegalArgumentException("본인에게 배정된 차량(" + truck.get().getVehicleNo() + ")만 등록할 수 있습니다.");
        }

        final String vNo = vehicleNo;
        TransportOrder order = orderRepository
                .findFirstByVehicleNoAndContainerNoAndStatusIn(vNo, containerNo, OrderStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "차량 " + vNo + " / 컨테이너 " + containerNo + " 로 배차된 운송오더가 없습니다."));

        // fix_1300: 조회만 한다. 예전에는 여기서 ASSIGNED → LOADED 로 바꾸고 기사도 배정해서
        //  T-02 인수 확인(씰 대조·개봉 보류)을 건너뛸 수 있었음. 적재(LOADED)는 SealService.containerPickup 만 바꾼다.
        if (order.getDriverAccountId() != null && !order.getDriverAccountId().equals(accountId)) {
            throw new IllegalStateException("내게 배정된 운송오더가 아닙니다.");
        }
        fillWeightAndHeight(order);
        orderRepository.save(order);
        return detail(order);
    }

    private void fillWeightAndHeight(TransportOrder order) {
        Container container = containerRepository.findById(order.getContainerNo()).orElse(null);
        VehicleSpec spec = order.getVehicleNo() == null ? null : vehicleSpecRepository.findById(order.getVehicleNo()).orElse(null);
        if (container != null) {
            BigDecimal chassis = spec != null && spec.getChassisHeightM() != null ? spec.getChassisHeightM() : DEFAULT_CHASSIS_HEIGHT_M;
            order.setLoadHeightM(chassis.add(ContainerNumbers.heightM(container.getIsoSizeTypeCode(), container.getIsHighCube()))
                    .setScale(2, RoundingMode.HALF_UP));
        }
        if (spec != null && spec.getEmptyWeightKg() != null && order.getVgmKg() != null) {
            order.setGrossWeightKg(spec.getEmptyWeightKg() + order.getVgmKg());
        }
    }

    // =====================================================================
    // 04 출발 전 점검 - 과적 자가점검 + 점검표 + 가까운 민간계량소
    // =====================================================================

    @Transactional
    public PrecheckResponse precheck(Long accountId, Long orderId, PrecheckRequest req) {
        TransportOrder order = myOrder(accountId, orderId);
        requireStatus(order, "출발 전 점검", OrderStatus.LOADED, OrderStatus.GATE_OUT);
        fillWeightAndHeight(order);

        VehicleSpec spec = vehicleSpecRepository.findById(order.getVehicleNo()).orElse(null);
        Integer empty = spec == null ? null : spec.getEmptyWeightKg();
        Integer gross = order.getGrossWeightKg();
        List<String> messages = new ArrayList<>();
        boolean passed = true;

        if (gross == null) {
            passed = false;
            messages.add(spec == null ? "차량 제원이 등록되지 않아 총중량을 계산할 수 없습니다." : "VGM(컨테이너 총중량) 정보가 없습니다.");
        } else {
            if (gross > grossLimitKg) {
                passed = false;
                messages.add("예상 총중량 " + gross + "kg 이 허용 총중량 " + grossLimitKg + "kg 을 넘습니다.");
            }
            if (spec != null && spec.getGrossWeightKg() != null && gross > spec.getGrossWeightKg()) {
                passed = false;
                messages.add("차량 제원상 총중량(" + spec.getGrossWeightKg() + "kg)을 넘습니다.");
            }
            if (passed) messages.add("총중량 기준 이내입니다. 축중(10톤)은 계량소에서 확인하세요.");
        }

        Map<String, Boolean> checklist = req.checklist() == null ? Map.of() : req.checklist();
        long unchecked = checklist.values().stream().filter(v -> !Boolean.TRUE.equals(v)).count();
        if (unchecked > 0) messages.add("점검표에 확인하지 않은 항목이 " + unchecked + "개 있습니다.");

        // 1차 overload_checks 테이블에도 "앱 자가점검" 기록으로 남김 (station_code = APP-SELF)
        if (gross != null) {
            Container container = containerRepository.findById(order.getContainerNo()).orElse(null);
            OverloadCheck check = OverloadCheck.builder()
                    .vehicleNo(order.getVehicleNo())
                    .containerNo(container == null ? null : order.getContainerNo())
                    .usagePurpose(spec == null ? null : spec.getUsagePurpose())
                    .emptyVehicleWeight(empty)
                    .totalWeight(gross)
                    .maxPayload(spec == null ? null : spec.getMaxPayloadKg())
                    .vgmWeight(order.getVgmKg())
                    .axleCount(spec == null ? null : spec.getAxleCount())
                    .isViolation(!passed)
                    .violationReason(passed ? null : String.join(" / ", messages))
                    .retryCount(0)
                    .isPassed(passed)
                    .stationCode("APP-SELF")
                    .build();
            order.setOverloadCheckId(overloadCheckRepository.save(check).getCheckId());
        }

        Map<String, Object> pre = new LinkedHashMap<>();
        pre.put("checklist", checklist);
        pre.put("passed", passed);
        pre.put("grossWeightKg", gross);
        pre.put("checkedAt", OffsetDateTime.now().toString());
        order.setPrecheck(writeJson(pre));
        orderRepository.save(order);

        if (!passed && gross != null) {
            notifyDriver(accountId, order, NotificationType.OVERLOAD_WARNING, "과적 자가점검 경고", String.join(" ", messages));
        }

        Terminal origin = terminalRepository.findById(order.getOriginTerminalId()).orElse(null);
        Double lat = req.lat() != null ? req.lat() : origin != null && origin.getLatitude() != null ? origin.getLatitude().doubleValue() : null;
        Double lng = req.lng() != null ? req.lng() : origin != null && origin.getLongitude() != null ? origin.getLongitude().doubleValue() : null;
        List<PlaceItem> nearby = lat == null ? List.of() : placeService.find("WEIGHBRIDGE", lat, lng, 30d, null, 3);

        double ratio = gross == null ? 0 : Math.min(1.5, gross / (double) grossLimitKg);
        return new PrecheckResponse(empty, order.getVgmKg(), gross, grossLimitKg, ratio, passed, messages, nearby, detail(order));
    }

    // =====================================================================
    // 05 운행 시작 / 경로
    // =====================================================================

    @Transactional
    public OrderDetail depart(Long accountId, Long orderId, DepartRequest req) {
        TransportOrder order = myOrder(accountId, orderId);
        requireStatus(order, "운행 시작", OrderStatus.LOADED, OrderStatus.GATE_OUT);
        fillWeightAndHeight(order);

        Terminal origin = terminalRepository.findById(order.getOriginTerminalId()).orElse(null);
        Hub hub = hubRepository.findById(order.getDestHubId()).orElseThrow();
        double fromLat, fromLng;
        if (req != null && req.lat() != null && req.lng() != null) {
            fromLat = req.lat();
            fromLng = req.lng();
        } else if (origin != null && origin.getLatitude() != null) {
            fromLat = origin.getLatitude().doubleValue();
            fromLng = origin.getLongitude().doubleValue();
        } else {
            throw new IllegalArgumentException("현재 위치를 알 수 없습니다. 위치 권한을 허용해 주세요.");
        }

        RouteResponse route = routeService.plan(fromLat, fromLng, hub, origin, order.getLoadHeightM(), order.getGrossWeightKg(), axleCount(order));
        OffsetDateTime now = OffsetDateTime.now();
        boolean noGateOut = order.is(OrderStatus.LOADED);

        order.setPlannedRoute(routeService.toJson(route));
        order.setPlannedDistanceKm(BigDecimal.valueOf(route.distanceM() / 1000).setScale(2, RoundingMode.HALF_UP));
        order.setEta(now.plusSeconds((long) route.durationS()));
        order.setDepartedAt(now);
        order.setStatus(OrderStatus.IN_TRANSIT.name());
        orderRepository.save(order);

        event(order, "DEPART", null, null, fromLat, fromLng,
                noGateOut ? "게이트아웃 기록 없이 운행 시작" : "운행 시작 (" + route.provider() + " 경로 " + order.getPlannedDistanceKm() + "km)");
        setTruckStatus(order.getVehicleNo(), "IN_TRANSIT");

        long hazards = route.heights().stream().filter(RoutePoi::hazard).count();
        if (hazards > 0) {
            notifyDriver(accountId, order, NotificationType.SYSTEM, "경로상 높이제한 구간 " + hazards + "곳",
                    "내 적재 높이 " + order.getLoadHeightM() + "m 보다 낮은 구간이 있습니다. 내비 화면의 경고를 확인하세요.");
        }
        return detail(order);
    }

    @Transactional
    public RouteResponse route(Long accountId, Long orderId) {
        TransportOrder order = myOrder(accountId, orderId);
        RouteResponse route = routeService.fromJson(order.getPlannedRoute());
        if (route != null) return route;
        // 아직 출발 전이면 터미널 → HUB 미리보기 경로 (저장하지 않음)
        Terminal origin = terminalRepository.findById(order.getOriginTerminalId()).orElse(null);
        Hub hub = hubRepository.findById(order.getDestHubId()).orElseThrow();
        if (origin == null || origin.getLatitude() == null) {
            throw new IllegalStateException("출발 터미널 좌표가 없어 경로를 계산할 수 없습니다.");
        }
        fillWeightAndHeight(order);
        return routeService.plan(origin.getLatitude().doubleValue(), origin.getLongitude().doubleValue(), hub, origin,
                order.getLoadHeightM(), order.getGrossWeightKg(), axleCount(order));
    }

    /**
     * 경로 이탈 시 현재 위치에서 다시 탐색. 외부 경로 API 한도를 아끼려고 60초 안에는 다시 계산하지 않는다.
     */
    @Transactional
    public RouteResponse reroute(Long accountId, Long orderId, RerouteRequest req) {
        TransportOrder order = myOrder(accountId, orderId);
        requireStatus(order, "경로 재탐색", OrderStatus.IN_TRANSIT);
        if (req == null || req.lat() == null || req.lng() == null) {
            throw new IllegalArgumentException("현재 위치(lat, lng)가 필요합니다.");
        }
        RouteResponse old = routeService.fromJson(order.getPlannedRoute());
        if (old != null && old.computedAt() != null
                && OffsetDateTime.parse(old.computedAt()).isAfter(OffsetDateTime.now().minusSeconds(60))) {
            return old;
        }
        Hub hub = hubRepository.findById(order.getDestHubId()).orElseThrow();
        Terminal origin = terminalRepository.findById(order.getOriginTerminalId()).orElse(null);
        RouteResponse route = routeService.plan(req.lat(), req.lng(), hub, origin,
                order.getLoadHeightM(), order.getGrossWeightKg(), axleCount(order));
        order.setPlannedRoute(routeService.toJson(route));
        order.setEta(OffsetDateTime.now().plusSeconds((long) route.durationS()));
        orderRepository.save(order);
        event(order, "REROUTE", null, null, req.lat(), req.lng(), "경로 재탐색 (" + route.provider() + ")");
        return route;
    }

    private Integer axleCount(TransportOrder order) {
        return order.getVehicleNo() == null ? null
                : vehicleSpecRepository.findById(order.getVehicleNo()).map(VehicleSpec::getAxleCount).orElse(null);
    }

    // =====================================================================
    // 06 도착 · 인계
    // =====================================================================

    @Transactional
    public OrderDetail arrive(Long accountId, Long orderId) {
        TransportOrder order = myOrder(accountId, orderId);
        requireStatus(order, "도착 처리", OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED);
        markArrived(order, "MANUAL", null, null);
        return detail(order);
    }

    /** TripMonitorService(지오펜스) 와 수동 도착 버튼이 같이 쓰는 도착 처리 */
    public void markArrived(TransportOrder order, String how, Double lat, Double lng) {
        if (order.is(OrderStatus.ARRIVED)) return;
        order.setStatus(OrderStatus.ARRIVED.name());
        order.setArrivedAt(OffsetDateTime.now());
        orderRepository.save(order);
        String hubName = hubRepository.findById(order.getDestHubId()).map(Hub::getBusinessName).orElse("HUB");
        event(order, "HUB_ENTER", "HUB", String.valueOf(order.getDestHubId()), lat, lng,
                hubName + " 도착 (" + ("MANUAL".equals(how) ? "기사 수동" : "지오펜스 자동") + ")");
        if (order.getDriverAccountId() != null) {
            notifyDriver(order.getDriverAccountId(), order, NotificationType.SYSTEM, "HUB 도착", hubName + "에 도착했습니다. 인계를 진행하세요."); // 26.10.29 "허브 에" 띄어쓰기
        }
    }

    @Transactional
    public HandoverResult handover(Long accountId, Long orderId, boolean containerNoConfirmed, boolean sealOk,
                                   boolean damageFree, String sealNo, String receiverName,
                                   List<MultipartFile> photos, MultipartFile signature) {
        TransportOrder order = myOrder(accountId, orderId);
        requireStatus(order, "인계", OrderStatus.IN_TRANSIT, OrderStatus.ARRIVED);
        if (!containerNoConfirmed) {
            throw new IllegalArgumentException("컨테이너 번호 일치 여부를 확인해 주세요.");
        }
        if (order.is(OrderStatus.IN_TRANSIT)) {
            markArrived(order, "MANUAL", null, null);
        }
        if (handoverRepository.findByOrderId(orderId).isPresent()) {
            throw new IllegalStateException("이미 인계가 완료된 오더입니다.");
        }

        List<String> photoPaths = new ArrayList<>();
        if (photos != null) {
            int i = 1;
            for (MultipartFile f : photos) {
                if (f != null && !f.isEmpty()) photoPaths.add(saveFile(orderId, "photo_" + (i++), f));
            }
        }

        Handover h = new Handover();
        h.setOrderId(orderId);
        h.setContainerNoConfirmed(true);
        h.setSealOk(sealOk);
        h.setDamageFree(damageFree);
        h.setSealNo(sealNo);
        h.setReceiverName(receiverName);
        h.setPhotoUrls(writeJson(photoPaths));
        h.setSignatureUrl(signature == null || signature.isEmpty() ? null : saveFile(orderId, "signature", signature));
        h.setCompletedAt(OffsetDateTime.now());
        handoverRepository.save(h);
        // fix_1300: 인계 씰 번호를 정답 씰과 대조 (불일치 → SEAL_MISMATCH·개봉 보류, 정답 없음 → 미등록 기록)
        String sealResult = sealService.handoverSeal(order, h, accountId);
        if ("MISMATCH".equals(sealResult)) h.setSealOk(false);
        handoverRepository.save(h);

        order.setStatus(OrderStatus.HANDED_OVER.name());
        order.setHandedOverAt(h.getCompletedAt());
        orderRepository.save(order);
        event(order, "HANDOVER", null, null, null, null,
                "인계 완료" + (receiverName == null || receiverName.isBlank() ? "" : " (인수자 " + receiverName + ")")
                        + (sealOk && damageFree ? "" : " - 이상 있음 표시"));
        setTruckStatus(order.getVehicleNo(), "OUTSIDE");
        // 26.10.29 QA: 인계를 마쳤으니 이 오더의 'HUB 도착 · 인계를 진행하세요' 알림은 읽음 (앱이 인계 뒤에 팝업을 다시 띄우지 않게)
        try { notificationRepository.markReadByReference(accountId, orderId, "HUB 도착", OffsetDateTime.now()); } catch (Exception e) { log.warn("도착 알림 읽음 처리 실패: {}", e.getMessage()); }

        double traveledKm = traveledKm(order);
        OffsetDateTime start = order.getGateOutAt() != null ? order.getGateOutAt() : order.getDepartedAt();
        long minutes = start == null ? 0 : Duration.between(start, order.getHandedOverAt()).toMinutes();
        return new HandoverResult(h.getHandoverId(), detail(order), traveledKm, minutes);
    }

    private double traveledKm(TransportOrder order) {
        List<TripLocation> locs = tripLocationRepository.findByOrderIdOrderByRecordedAtAsc(order.getOrderId());
        double m = 0;
        for (int i = 1; i < locs.size(); i++) {
            TripLocation a = locs.get(i - 1), b = locs.get(i);
            m += GeoUtils.distanceM(a.getLatitude(), a.getLongitude(), b.getLatitude().doubleValue(), b.getLongitude().doubleValue());
        }
        if (m == 0 && order.getPlannedDistanceKm() != null) return order.getPlannedDistanceKm().doubleValue();
        return Math.round(m / 100) / 10.0;
    }

    private String saveFile(Long orderId, String baseName, MultipartFile file) {
        try {
            String ext = com.scargo.config.UploadStorage.checkedExt(file); // fix_1300: jpg·png·pdf 만
            Path dir = com.scargo.config.UploadStorage.dir("app/handover/" + orderId);
            Path target = dir.resolve(baseName + "_" + System.currentTimeMillis() + ext);
            file.transferTo(target.toFile());
            return "/uploads/app/handover/" + orderId + "/" + target.getFileName(); // fix_1300: /uploads/** 로 바로 열 수 있는 경로
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패: " + e.getMessage());
        }
    }

    // =====================================================================
    // 07 운송 이력
    // =====================================================================

    @Transactional(readOnly = true)
    public HistoryResponse history(Long accountId, String range) {
        String r = range == null ? "today" : range;
        LocalDate today = LocalDate.now(KST);
        LocalDate fromDate = switch (r) {
            case "week" -> today.minusDays(today.getDayOfWeek().getValue() - 1L);
            case "month" -> today.withDayOfMonth(1);
            default -> today;
        };
        OffsetDateTime from = fromDate.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

        List<OrderSummary> items = orderRepository
                .findByDriverAccountIdAndStatusAndHandedOverAtBetweenOrderByHandedOverAtDesc(accountId, "HANDED_OVER", from, to)
                .stream().map(this::summary).toList();

        BigDecimal total = items.stream().map(OrderSummary::distanceKm).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long overload = items.stream().filter(OrderSummary::overloadWarning).count();
        long deviations = items.stream().mapToLong(OrderSummary::deviationCount).sum();
        return new HistoryResponse(r, new HistorySummary(items.size(), total, overload, deviations), items);
    }

    // =====================================================================
    // 1차 연동: OUT 게이트 OCR(EXIT) → 운송오더 GATE_OUT
    // =====================================================================

    /**
     * GateLogService 에서 호출. 게이트 기록이 DB 에 커밋된 "뒤에" 처리해야
     * 이쪽에서 오류가 나도 게이트 기록 저장이 롤백되지 않는다.
     */
    public void scheduleGateOut(String vehicleNo, Long gateLogId) {
        Runnable task = () -> {
            try {
                self.onGateOut(vehicleNo, gateLogId);
            } catch (Exception e) {
                log.warn("[{}] 게이트아웃 → 운송오더 연동 실패: {}", vehicleNo, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onGateOut(String vehicleNo, Long gateLogId) {
        GateLog gateLog = gateLogRepository.findById(gateLogId).orElse(null);
        if (gateLog == null) return;

        List<TransportOrder> candidates = orderRepository.findByVehicleNoAndStatusInOrderByAssignedAtAsc(
                vehicleNo, List.of("ASSIGNED", "LOADED"));
        if (candidates.isEmpty()) {
            log.info("[{}] 게이트아웃 - 대기 중인 운송오더 없음", vehicleNo);
            return;
        }
        // 적재 등록(LOADED)한 오더를 우선
        TransportOrder order = candidates.stream().filter(o -> o.is(OrderStatus.LOADED)).findFirst().orElse(candidates.get(0));

        Gate gate = gateLog.getGate();
        if (gate != null && gate.getTerminalId() != null) {
            order.setOriginTerminalId(gate.getTerminalId());
        }
        order.setStatus(OrderStatus.GATE_OUT.name());
        order.setGateOutLogId(gateLogId);
        order.setGateOutAt(gateLog.getPassAt() != null ? gateLog.getPassAt() : OffsetDateTime.now());
        if (order.getLoadedAt() == null) order.setLoadedAt(order.getGateOutAt());
        fillWeightAndHeight(order);
        orderRepository.save(order);

        String hubName = hubRepository.findById(order.getDestHubId()).map(Hub::getBusinessName).orElse("HUB");
        event(order, "GATE_OUT", "GATE", gate == null ? null : String.valueOf(gate.getGateId()),
                gate == null || gate.getLatitude() == null ? null : gate.getLatitude().doubleValue(),
                gate == null || gate.getLongitude() == null ? null : gate.getLongitude().doubleValue(),
                (gate == null ? "" : gate.getGateName() + " ") + "게이트아웃");
        if (order.getDriverAccountId() != null) {
            notifyDriver(order.getDriverAccountId(), order, NotificationType.SYSTEM, "게이트아웃 완료",
                    order.getContainerNo() + " · 목적지 " + hubName + " 자동 설정");
        }
        log.info("[{}] 게이트아웃 → 운송오더 {} GATE_OUT", vehicleNo, order.getOrderNo());
    }

    /**
     * PC 테스트용: OCR 장비 없이 OUT 게이트 통과를 흉내 낸다. (scargo.app.dev-tools=true 일 때만 컨트롤러가 열림)
     * 트랜잭션 밖에서 게이트 기록을 먼저 저장(커밋)한 뒤 onGateOut 을 부른다.
     */
    public OrderDetail devGateOut(Long accountId, Long orderId) {
        TransportOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("오더가 없습니다."));
        checkOwner(accountId, order);
        requireStatus(order, "게이트아웃 시뮬레이션", OrderStatus.ASSIGNED, OrderStatus.LOADED);

        Gate gate = gateRepository.findByIsActiveTrue().stream()
                .filter(g -> "OUT".equals(g.getGateType()) || "BOTH".equals(g.getGateType()))
                .sorted(Comparator.comparing((Gate g) -> !Objects.equals(g.getTerminalId(), order.getOriginTerminalId())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OUT 게이트가 등록되어 있지 않습니다. (03 데모데이터 실행 필요)"));

        GateLog gl = GateLog.builder()
                .gate(gate)
                .recognizedPlateNo(order.getVehicleNo())
                .actualVehicleNo(order.getVehicleNo())
                .plateConfidence(new BigDecimal("99.00"))
                .recognitionStatus("SUCCESS")
                .ocrRawData("{\"scanType\":\"EXIT\",\"source\":\"APP_DEV_SIMULATION\"}")
                .vehicleType("TRACTOR")
                .build();
        GateLog saved = gateLogRepository.save(gl);       // 여기서 커밋됨
        self.onGateOut(order.getVehicleNo(), saved.getGateLogId());
        return self.getDetail(accountId, orderId);
    }

    // =====================================================================
    // 공통
    // =====================================================================

    public Optional<Truck> myTruck(Long accountId) {
        return truckRepository.findByAssignedDriver_AccountId(accountId);
    }

    public TransportOrder myOrder(Long accountId, Long orderId) {
        TransportOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오더입니다. ID: " + orderId));
        checkOwner(accountId, order);
        return order;
    }

    /**
     * 26.10.27: 본인 오더 = 배차 기사(driver_account_id)가 나. 배차 기사가 비어 있을 때만 내 전담 차량으로 판단
     *  (lookup·SealService·V9AppController 와 같은 기준. 예전엔 차량만 같아도 다른 기사의 오더를 조회·도착·인계할 수 있었음)
     */
    private void checkOwner(Long accountId, TransportOrder order) {
        boolean mine = order.getDriverAccountId() != null
                ? Objects.equals(order.getDriverAccountId(), accountId)
                : myTruck(accountId).map(t -> t.getVehicleNo().equals(order.getVehicleNo())).orElse(false);
        if (!mine) throw new IllegalArgumentException("본인에게 배차된 오더가 아닙니다.");
    }

    private static void requireStatus(TransportOrder order, String action, OrderStatus... allowed) {
        for (OrderStatus s : allowed) {
            if (order.is(s)) return;
        }
        throw new IllegalStateException(action + "을(를) 할 수 없는 상태입니다. (현재: " + order.getStatus() + ")");
    }

    public void event(TransportOrder order, String type, String refType, String refId, Double lat, Double lng, String message) {
        TripEvent e = new TripEvent();
        e.setOrderId(order.getOrderId());
        e.setEventType(type);
        e.setRefType(refType);
        e.setRefId(refId);
        e.setLatitude(lat == null ? null : BigDecimal.valueOf(lat));
        e.setLongitude(lng == null ? null : BigDecimal.valueOf(lng));
        e.setMessage(message);
        e.setOccurredAt(OffsetDateTime.now());
        tripEventRepository.save(e);
    }

    public void notifyDriver(Long accountId, TransportOrder order, NotificationType type, String title, String message) {
        try {
            notificationService.createNotificationInNewTx(NotificationCreateRequest.builder()
                    .accountId(accountId)
                    .title(title)
                    .message(message)
                    .notificationType(type)
                    .referenceId(order.getOrderId())
                    .build());
        } catch (Exception e) {
            log.warn("알림 저장 실패: {}", e.getMessage());
        }
    }

    private void setTruckStatus(String vehicleNo, String status) {
        if (vehicleNo == null) return;
        truckRepository.findById(vehicleNo).ifPresent(t -> {
            t.setStatus(status);
            truckRepository.save(t);
        });
    }

    public String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    // ---------- 응답 변환 ----------

    public OrderDetail detail(TransportOrder o) {
        Container c = containerRepository.findById(o.getContainerNo()).orElse(null);
        ContainerInfo container = c == null
                ? new ContainerInfo(o.getContainerNo(), null, null, null, null, null)
                : new ContainerInfo(c.getContainerNo(), c.getIsoSizeTypeCode(), c.getIsHighCube(), c.getMaxGrossKg(),
                c.getTareKg(), ContainerNumbers.heightM(c.getIsoSizeTypeCode(), c.getIsHighCube()));

        Terminal t = terminalRepository.findById(o.getOriginTerminalId()).orElse(null);
        BigDecimal gateLimit = t == null ? null : minLimit(heightRestrictionRepository.findByTargetTypeAndTerminalId("TERMINAL_GATE", t.getTerminalId()));
        TerminalInfo origin = t == null ? null : new TerminalInfo(t.getTerminalId(), t.getTerminalCode(), t.getTerminalName(),
                t.getPortName(), t.getOperator(), t.getAddress(), t.getLatitude(), t.getLongitude(), t.getCoordVerified(),
                gateLimit, t.getGateHeightNote());

        Hub h = hubRepository.findById(o.getDestHubId()).orElse(null);
        BigDecimal hubLimit = h == null ? null : minLimit(heightRestrictionRepository.findByTargetTypeAndHubId("HUB_ENTRANCE", h.getHubId()));
        HubInfo dest = h == null ? null : new HubInfo(h.getHubId(), h.getBusinessName(), h.getRoadAddress(), h.getLatitude(),
                h.getLongitude(), h.getSizeClass(), h.getIsMega(), h.getTotalAreaM2(), hubLimit, h.getGeofenceRadiusM());

        GateOutInfo gateOut = null;
        if (o.getGateOutLogId() != null) {
            GateLog g = gateLogRepository.findById(o.getGateOutLogId()).orElse(null);
            if (g != null) {
                gateOut = new GateOutInfo(g.getGateLogId(), g.getGate() == null ? null : g.getGate().getGateName(),
                        g.getPassAt() != null ? g.getPassAt() : o.getGateOutAt(), g.getRecognizedPlateNo(),
                        Objects.equals(g.getActualVehicleNo(), o.getVehicleNo()));
            }
        }

        List<Warning> warnings = new ArrayList<>();
        BigDecimal need = o.getLoadHeightM() == null ? null : o.getLoadHeightM().add(RouteService.HEIGHT_MARGIN_M);
        if (need != null && gateLimit != null && gateLimit.compareTo(need) < 0) {
            warnings.add(new Warning("HEIGHT", "DANGER", "출발 게이트 높이제한 " + gateLimit + "m",
                    "내 적재 높이 " + o.getLoadHeightM() + "m - 게이트 통과 전 확인하세요."));
        }
        if (need != null && hubLimit != null && hubLimit.compareTo(need) < 0) {
            warnings.add(new Warning("HEIGHT", "DANGER", "도착 HUB 진입로 높이제한 " + hubLimit + "m",
                    "내 적재 높이 " + o.getLoadHeightM() + "m - 다른 진입로를 HUB 에 문의하세요."));
        }
        if (o.getGrossWeightKg() != null && o.getGrossWeightKg() > grossLimitKg) {
            warnings.add(new Warning("WEIGHT", "DANGER", "총중량 초과 예상",
                    "예상 총중량 " + o.getGrossWeightKg() + "kg (허용 " + grossLimitKg + "kg)"));
        }
        if (o.getVehicleNo() != null && vehicleSpecRepository.findById(o.getVehicleNo()).isEmpty()) {
            warnings.add(new Warning("SPEC", "INFO", "차량 제원 미등록", "총중량·적재 높이는 기본값으로 계산됩니다."));
        }

        return new OrderDetail(o.getOrderId(), o.getOrderNo(), o.getStatus(), o.getVehicleNo(), container, o.getVgmKg(),
                o.getGrossWeightKg(), o.getLoadHeightM(), origin, dest, gateOut, readMap(o.getPrecheck()),
                o.getPlannedDistanceKm(), o.getEta(), o.getAssignedAt(), o.getLoadedAt(), o.getGateOutAt(),
                o.getDepartedAt(), o.getArrivedAt(), o.getHandedOverAt(), warnings);
    }

    public OrderSummary summary(TransportOrder o) {
        String originName = terminalRepository.findById(o.getOriginTerminalId()).map(Terminal::getTerminalName).orElse(null);
        Hub h = hubRepository.findById(o.getDestHubId()).orElse(null);
        Map<String, Object> pre = readMap(o.getPrecheck());
        boolean overload = pre != null && Boolean.FALSE.equals(pre.get("passed"));
        long deviations = tripEventRepository.countByOrderIdAndEventType(o.getOrderId(), "ROUTE_DEVIATION");
        return new OrderSummary(o.getOrderId(), o.getOrderNo(), o.getStatus(), o.getContainerNo(), originName,
                h == null ? null : h.getBusinessName(), h == null ? null : h.getRoadAddress(),
                o.getAssignedAt(), o.getGateOutAt(), o.getHandedOverAt(), o.getPlannedDistanceKm(), deviations, overload);
    }

    private static BigDecimal minLimit(List<HeightRestriction> list) {
        return list.stream().map(HeightRestriction::getLimitHeightM).min(BigDecimal::compareTo).orElse(null);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
