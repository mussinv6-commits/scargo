package com.scargo.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 26.10.03 2차: 기사 앱 API 요청/응답 모음.
 * 화면(와이어프레임) 단위로 필요한 값만 담는다.
 */
public final class AppDtos {

    private AppDtos() {
    }

    // ---------- 인증 / 내 정보 (01 로그인) ----------

    public record LoginRequest(String userId, String userPw) {
    }

    public record LoginResponse(String token, OffsetDateTime expiresAt, MeResponse me) {
    }

    public record MeResponse(Long accountId, String userId, String userName, String phoneNum,
                             Long companyId, String companyName, String userType, TruckInfo truck) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TruckInfo(String vehicleNo, String trailerNo, boolean semiTrailer, String truckType,
                            BigDecimal maxLoadWeight, String status, SpecInfo spec) {
    }

    public record SpecInfo(String usagePurpose, Integer emptyWeightKg, Integer grossWeightKg, Integer maxPayloadKg,
                           Integer axleCount, Integer tireCount, BigDecimal chassisHeightM) {
    }

    // ---------- 오더 (02 홈, 03 적재 등록) ----------

    public record LookupRequest(String vehicleNo, String containerNo) {
    }

    public record ContainerInfo(String containerNo, String isoSizeTypeCode, Boolean highCube,
                                BigDecimal maxGrossKg, BigDecimal tareKg, BigDecimal heightM) {
    }

    public record TerminalInfo(Long terminalId, String code, String name, String portName, String operator,
                               String address, BigDecimal lat, BigDecimal lng, Boolean coordVerified,
                               BigDecimal gateHeightLimitM, String gateHeightNote) {
    }

    public record HubInfo(Long hubId, String name, String address, BigDecimal lat, BigDecimal lng,
                          String sizeClass, Boolean mega, BigDecimal totalAreaM2,
                          BigDecimal entranceHeightLimitM, Integer geofenceRadiusM) {
    }

    public record GateOutInfo(Long gateLogId, String gateName, OffsetDateTime passAt,
                              String recognizedPlateNo, boolean plateMatched) {
    }

    public record OrderDetail(Long orderId, String orderNo, String status, String vehicleNo,
                              ContainerInfo container, Integer vgmKg, Integer grossWeightKg, BigDecimal loadHeightM,
                              TerminalInfo origin, HubInfo dest, GateOutInfo gateOut,
                              Map<String, Object> precheck, BigDecimal plannedDistanceKm, OffsetDateTime eta,
                              OffsetDateTime assignedAt, OffsetDateTime loadedAt, OffsetDateTime gateOutAt,
                              OffsetDateTime departedAt, OffsetDateTime arrivedAt, OffsetDateTime handedOverAt,
                              List<Warning> warnings) {
    }

    /** 화면 상단 경고 (높이·중량 등) */
    public record Warning(String type, String level, String title, String message) {
    }

    public record OrderSummary(Long orderId, String orderNo, String status, String containerNo,
                               String originName, String destName, String destAddress,
                               OffsetDateTime assignedAt, OffsetDateTime gateOutAt, OffsetDateTime handedOverAt,
                               BigDecimal distanceKm, long deviationCount, boolean overloadWarning) {
    }

    public record HomeResponse(OrderDetail current, List<OrderSummary> waiting, long unreadNotifications) {
    }

    // ---------- 출발 전 점검 (04) ----------

    public record PrecheckRequest(Map<String, Boolean> checklist, Double lat, Double lng) {
    }

    public record PrecheckResponse(Integer emptyWeightKg, Integer vgmKg, Integer grossWeightKg, int grossLimitKg,
                                   double ratio, boolean passed, List<String> messages,
                                   List<PlaceItem> nearbyWeighbridges, OrderDetail order) {
    }

    // ---------- 내비 (05) ----------

    public record DepartRequest(Double lat, Double lng) {
    }

    public record RoutePoi(String type, String id, String name, double lat, double lng, double alongM,
                           BigDecimal limitHeightM, boolean hazard) {
    }

    /** 턴바이턴 안내 1건 (alongM = 출발점부터 경로를 따라간 거리) */
    public record Instruction(double alongM, double lat, double lng, String type, String text, String roadName) {
    }

    public record RouteResponse(String provider, double distanceM, double durationS,
                                List<double[]> path, List<RoutePoi> tolls, List<RoutePoi> checkpoints,
                                List<RoutePoi> heights, List<Instruction> instructions, String computedAt) {
    }

    public record RerouteRequest(Double lat, Double lng) {
    }

    public record LocationRequest(Double lat, Double lng, Double speedKmh, Integer heading, Double accuracyM,
                                  OffsetDateTime recordedAt) {
    }

    public record Alert(String type, String level, String title, String message, Double distanceAheadM) {
    }

    public record TollProgress(String code, String name, double alongM, boolean passed) {
    }

    public record TripStatus(String status, boolean arrived, double traveledM, double remainingM,
                             double remainingS, OffsetDateTime eta, double distanceFromRouteM, boolean offRoute,
                             List<Alert> alerts, List<TollProgress> tolls) {
    }

    // ---------- 도착·인계 (06), 이력 (07) ----------

    public record HandoverResult(Long handoverId, OrderDetail order, double traveledKm, long durationMinutes) {
    }

    public record HistorySummary(long count, BigDecimal totalDistanceKm, long overloadWarnings, long deviations) {
    }

    public record HistoryResponse(String range, HistorySummary summary, List<OrderSummary> items) {
    }

    // ---------- 알림 (08), 정보 조회 (09) ----------

    public record NotificationItem(Long id, String title, String message, String type, boolean read,
                                   OffsetDateTime createdAt) {
    }

    public record PlaceItem(String type, String id, String name, String address, double lat, double lng,
                            Double distanceM, Map<String, Object> extra) {
    }
}
