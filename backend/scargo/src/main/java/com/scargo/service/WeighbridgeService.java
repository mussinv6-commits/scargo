package com.scargo.service;

import com.scargo.dto.OverloadCheckCreateRequest;
import com.scargo.dto.OverloadCheckResponse;
import com.scargo.dto.WeighbridgeQueueItem;
import com.scargo.dto.WeighingRequest;
import com.scargo.dto.WeighingResultResponse;
import com.scargo.dto.WeighingResultResponse.Violation;
import com.scargo.entity.Container;
import com.scargo.entity.GateLog;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.OverloadCheck;
import com.scargo.entity.Truck;
import com.scargo.repository.ContainerRepository;
import com.scargo.repository.GateLogRepository;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 26.10.01 추가(계중대 정식화)
 * 게이트 OCR 통과 → 계중대 계량 → 과적 판정 → overload_checks 저장 흐름을 담당한다.
 *
 * 판정 기준(기본값, 도로법 시행령 제79조의 운행 제한 기준):
 *   - 축하중 10톤(10,000kg) 초과
 *   - 총중량 40톤(40,000kg) 초과
 * application.yml 에서 scargo.weighbridge.* 로 바꿀 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeighbridgeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GateLogRepository gateLogRepository;
    private final OverloadCheckRepository overloadCheckRepository;
    private final TruckRepository truckRepository;
    private final OverloadService overloadService; // 저장 + 위반 시 업체 알림 생성은 기존 로직 재사용
    private final ContainerRepository containerRepository;         // 26.10.01 추가: 컨테이너 자동 조회
    private final LoadingRecordRepository loadingRecordRepository; // 26.10.01 추가
    private final ObjectMapper objectMapper;                       // 26.10.01 추가: trucks.planned_route(JSON) 읽기용

    @Value("${scargo.weighbridge.axle-limit-kg:10000}")
    private int axleLimitKg;

    @Value("${scargo.weighbridge.gross-limit-kg:40000}")
    private int grossLimitKg;

    @Value("${scargo.weighbridge.station-code:WB-01}")
    private String defaultStationCode;

    @Value("${scargo.weighbridge.queue-hours:12}")
    private int queueHours;

    public Map<String, Object> getPolicy() {
        return Map.of(
                "stationCode", defaultStationCode,
                "axleLimitKg", axleLimitKg,
                "grossLimitKg", grossLimitKg,
                "queueHours", queueHours
        );
    }

    // 1. 계량 대기열: 게이트를 통과(등록차량 매칭)했지만 아직 계량하지 않은 차량
    public List<WeighbridgeQueueItem> getQueue() {
        OffsetDateTime since = OffsetDateTime.now(KST).minusHours(queueHours);
        List<GateLog> logs = gateLogRepository.findWeighbridgeQueue(since);
        if (logs.isEmpty()) {
            return List.of();
        }
        List<String> plates = logs.stream().map(GateLog::getActualVehicleNo).distinct().toList();
        Map<String, Truck> trucks = truckRepository.findAllById(plates).stream()
                .collect(Collectors.toMap(Truck::getVehicleNo, Function.identity()));
        return logs.stream()
                .map(log -> {
                    String no = log.getActualVehicleNo();
                    ContainerHit hit = resolveContainer(no);
                    return new WeighbridgeQueueItem(log, no, trucks.get(no), hit.container, hit.source);
                })
                .toList();
    }

    // 1-1. 26.10.01 추가: 차량번호로 등록차량 + 실린 컨테이너 정보 조회 (직접 계량 / 재계량 화면용)
    public WeighbridgeQueueItem getVehicleInfo(String vehicleNo) {
        String no = vehicleNo.trim().replace(" ", "");
        Truck truck = truckRepository.findById(no).orElse(null);
        ContainerHit hit = resolveContainer(no);
        return new WeighbridgeQueueItem(null, no, truck, hit.container, hit.source);
    }

    /**
     * 26.10.01 추가: 이 차량에 실린 컨테이너를 DB에서 찾는다.
     *  1순위: 컨테이너-차량 배정(매핑) - containers.assigned_vehicle_no
     *  2순위: 차량 운행정보에 들어있는 컨테이너 - trucks.planned_route 의 "container" (실제 데이터가 여기 있음)
     *  3순위: 이 차량의 가장 최근 적재기록 - loading_records
     */
    private ContainerHit resolveContainer(String vehicleNo) {
        if (vehicleNo == null || vehicleNo.isBlank()) {
            return ContainerHit.NONE;
        }
        Optional<Container> mapped = containerRepository.findByAssignedVehicleNo(vehicleNo);
        if (mapped.isPresent()) {
            return new ContainerHit(mapped.get(), "MAPPING");
        }
        Container planned = containerFromPlannedRoute(vehicleNo);
        if (planned != null) {
            return new ContainerHit(planned, "PLANNED_ROUTE");
        }
        return loadingRecordRepository.findFirstByTruck_VehicleNoOrderByRecordIdDesc(vehicleNo)
                .map(LoadingRecord::getContainer)
                .map(c -> new ContainerHit(c, "LOADING_RECORD"))
                .orElse(ContainerHit.NONE);
    }

    /**
     * 26.10.01 추가: trucks.planned_route JSON 의 {"container": {"container_no": ..., "tare_kg": ..., "net_kg": ...}} 에서
     * 컨테이너를 꺼낸다. containers 테이블에 같은 번호가 있으면 그 정보를 쓰고,
     * 없으면 JSON 값으로 화면 표시용 객체만 만든다(저장하지 않음).
     */
    private Container containerFromPlannedRoute(String vehicleNo) {
        String json = truckRepository.findById(vehicleNo).map(Truck::getPlannedRoute).orElse(null);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode c = objectMapper.readTree(json).path("container");
            String no = text(c, "container_no", "containerNo");
            if (no == null) {
                return null;
            }
            Optional<Container> saved = containerRepository.findById(no);
            if (saved.isPresent()) {
                return saved.get();
            }
            BigDecimal tare = num(c, "tare_kg", "tareKg");
            BigDecimal net = num(c, "net_kg", "netKg");
            BigDecimal maxGross = num(c, "max_gross_kg", "maxGrossKg");
            if (maxGross == null && tare != null && net != null) {
                maxGross = tare.add(net);
            }
            return Container.builder()
                    .containerNo(no)
                    .isoSizeTypeCode(text(c, "iso_size_type_code", "isoSizeTypeCode", "size_type"))
                    .containerType(text(c, "container_type", "containerType", "type"))
                    .tareKg(tare)
                    .netKg(net)
                    .maxGrossKg(maxGross)
                    .build();
        } catch (Exception e) {
            return null; // JSON 형식이 다르면 컨테이너 없음으로 처리
        }
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) {
                return v.asText().trim();
            }
        }
        return null;
    }

    private static BigDecimal num(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.path(k);
            if (v.isNumber()) {
                return v.decimalValue();
            }
            if (v.isTextual()) {
                try { return new BigDecimal(v.asText().trim()); } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private record ContainerHit(Container container, String source) {
        static final ContainerHit NONE = new ContainerHit(null, null);
    }

    // 2. 오늘 계량 기록 (최신순)
    public List<OverloadCheckResponse> getTodayRecords() {
        OffsetDateTime startOfDay = LocalDate.now(KST).atStartOfDay(KST).toOffsetDateTime();
        return overloadCheckRepository.findTop50ByCheckedAtGreaterThanEqualOrderByCheckIdDesc(startOfDay)
                .stream().map(OverloadCheckResponse::new).toList();
    }

    // 3. 계량 저장: 축중만 받아서 서버가 판정 후 overload_checks 에 저장
    @Transactional
    public WeighingResultResponse weigh(WeighingRequest request) {
        String vehicleNo = request.getVehicleNo().trim().replace(" ", "");

        if (request.getGateLogId() != null) {
            GateLog gateLog = gateLogRepository.findById(request.getGateLogId())
                    .orElseThrow(() -> new IllegalArgumentException("게이트 통과 기록을 찾을 수 없습니다. id=" + request.getGateLogId()));
            if (overloadCheckRepository.existsByGateLogId(gateLog.getGateLogId())) {
                throw new IllegalStateException("이미 계량을 마친 차량입니다. (" + vehicleNo + ")");
            }
        }

        Measurement m = Measurement.of(request.getAxles());
        List<Violation> violations = judge(m);
        boolean isViolation = !violations.isEmpty();

        // 26.10.01 변경: 컨테이너 번호는 화면 입력이 아니라 DB(차량 배정/적재기록)에서 자동으로 채움
        String containerNo = blankToNull(request.getContainerNo());
        if (containerNo == null) {
            Container c = resolveContainer(vehicleNo).container;
            containerNo = c != null ? c.getContainerNo() : null;
        }

        OverloadCheckCreateRequest create = OverloadCheckCreateRequest.builder()
                .vehicleNo(vehicleNo)
                .containerNo(containerNo)
                .usagePurpose("계중대 계량")
                .maxPayload(maxPayloadKg(vehicleNo)) // 26.10.01 추가: 등록차량의 최대 적재중량(trucks.max_load_weight)
                .totalWeight(m.total)
                .axleCount(m.weights.length)
                .vehicleAxleCount(m.weights.length)
                .axle1Weight(m.w(0)).axle2Weight(m.w(1)).axle3Weight(m.w(2)).axle4Weight(m.w(3))
                .axle5Weight(m.w(4)).axle6Weight(m.w(5)).axle7Weight(m.w(6)).axle8Weight(m.w(7))
                .axle1WheelLeft(m.l(0)).axle1WheelRight(m.r(0))
                .axle2WheelLeft(m.l(1)).axle2WheelRight(m.r(1))
                .axle3WheelLeft(m.l(2)).axle3WheelRight(m.r(2))
                .axle4WheelLeft(m.l(3)).axle4WheelRight(m.r(3))
                .axle5WheelLeft(m.l(4)).axle5WheelRight(m.r(4))
                .axle6WheelLeft(m.l(5)).axle6WheelRight(m.r(5))
                .axle7WheelLeft(m.l(6)).axle7WheelRight(m.r(6))
                .axle8WheelLeft(m.l(7)).axle8WheelRight(m.r(7))
                .isViolation(isViolation)
                .violationReason(isViolation ? describe(violations) : null)
                .retryCount(0)
                .isPassed(!isViolation)
                .gateLogId(request.getGateLogId())
                .stationCode(stationOrDefault(request.getStationCode()))
                .build();

        Long checkId = overloadService.createCheck(create).getCheckId();
        return toResult(checkId, violations);
    }

    // 4. 감량 후 재계량: 같은 기록을 새 측정값으로 갱신하고 재검증 횟수 +1
    @Transactional
    public WeighingResultResponse reweigh(Long checkId, WeighingRequest request) {
        OverloadCheck check = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("계량 기록을 찾을 수 없습니다. id=" + checkId));
        if (Boolean.TRUE.equals(check.getIsPassed())) {
            throw new IllegalStateException("이미 통과한 차량은 재계량할 필요가 없습니다.");
        }

        Measurement m = Measurement.of(request.getAxles());
        List<Violation> violations = judge(m);
        boolean isViolation = !violations.isEmpty();

        check.applyReweigh(m.weights, m.left, m.right, m.total, isViolation,
                isViolation ? describe(violations) : null);
        overloadCheckRepository.saveAndFlush(check);
        return toResult(checkId, violations);
    }

    // ---- 내부 도우미 ----

    private WeighingResultResponse toResult(Long checkId, List<Violation> violations) {
        overloadCheckRepository.stampCheckedAtIfMissing(checkId);
        OverloadCheck saved = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalStateException("저장된 계량 기록을 다시 읽지 못했습니다. id=" + checkId));
        return new WeighingResultResponse(new OverloadCheckResponse(saved), violations, axleLimitKg, grossLimitKg);
    }

    private List<Violation> judge(Measurement m) {
        List<Violation> list = new ArrayList<>();
        for (int i = 0; i < m.weights.length; i++) {
            if (m.weights[i] > axleLimitKg) {
                list.add(new Violation("AXLE", i + 1, m.weights[i], axleLimitKg));
            }
        }
        if (m.total > grossLimitKg) {
            list.add(new Violation("GROSS", null, m.total, grossLimitKg));
        }
        return list;
    }

    private static String describe(List<Violation> violations) {
        return violations.stream()
                .map(v -> "GROSS".equals(v.getType())
                        ? String.format("총중량 %,dkg (기준 %,dkg 초과)", v.getMeasuredKg(), v.getLimitKg())
                        : String.format("%d축 %,dkg (축하중 기준 %,dkg 초과)", v.getAxleNo(), v.getMeasuredKg(), v.getLimitKg()))
                .collect(Collectors.joining(", "));
    }

    // 26.10.01 추가: 등록차량(trucks)의 최대 적재중량(kg)을 정수로 - 미등록/미입력이면 null
    private Integer maxPayloadKg(String vehicleNo) {
        return truckRepository.findById(vehicleNo)
                .map(Truck::getMaxLoadWeight)
                .map(WeighbridgeService::toKg)
                .orElse(null);
    }

    /** trucks.max_load_weight 는 톤 단위(예: 26.00)로 들어있음 → kg 로 변환. 1,000 이상이면 이미 kg 로 보고 그대로 */
    public static Integer toKg(BigDecimal w) {
        if (w == null) return null;
        BigDecimal kg = w.compareTo(BigDecimal.valueOf(1000)) < 0 ? w.multiply(BigDecimal.valueOf(1000)) : w;
        return kg.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String stationOrDefault(String code) {
        return code == null || code.isBlank() ? defaultStationCode : code.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toUpperCase();
    }

    /** 요청의 축 목록을 배열로 정리 (좌/우 윤중이 없으면 축중을 반씩 나눠 채움) */
    private static final class Measurement {
        final Integer[] weights;
        final Integer[] left;
        final Integer[] right;
        final int total;

        private Measurement(Integer[] weights, Integer[] left, Integer[] right, int total) {
            this.weights = weights;
            this.left = left;
            this.right = right;
            this.total = total;
        }

        static Measurement of(List<WeighingRequest.Axle> axles) {
            int n = axles.size();
            Integer[] w = new Integer[n];
            Integer[] l = new Integer[n];
            Integer[] r = new Integer[n];
            int total = 0;
            for (int i = 0; i < n; i++) {
                WeighingRequest.Axle a = axles.get(i);
                w[i] = a.getWeightKg();
                if (a.getLeftKg() != null && a.getRightKg() != null) {
                    l[i] = a.getLeftKg();
                    r[i] = a.getRightKg();
                } else {
                    l[i] = w[i] / 2;
                    r[i] = w[i] - l[i];
                }
                total += w[i];
            }
            return new Measurement(w, l, r, total);
        }

        Integer w(int i) { return i < weights.length ? weights[i] : 0; }
        Integer l(int i) { return i < left.length ? left[i] : 0; }
        Integer r(int i) { return i < right.length ? right[i] : 0; }
    }
}
