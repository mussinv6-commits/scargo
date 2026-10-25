package com.scargo.amr.service;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.entity.AmrRobot;
import com.scargo.amr.entity.AmrTaskLog;
import com.scargo.amr.entity.HubVehicleLog;
import com.scargo.amr.repository.AmrRobotRepository;
import com.scargo.amr.repository.AmrTaskLogRepository;
import com.scargo.amr.repository.AmrTelemetryRepository;
import com.scargo.amr.repository.HubVehicleLogRepository;
import com.scargo.amr.security.AmrAccessPolicy.Access;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

// 26.10.15 KPI: DB에 쌓인 기록으로 오늘 운영 지표를 계산한다 (앱 통계 탭 · 웹 KPI 카드)
//  - 처리량 · 시간대별 · 평균 운반 시간 · 교차로 대기: amr_task_logs
//  - 로봇 가동률(최근 1시간): amr_telemetry 의 상태 기록 중 '작업 중' 비율
//  - 입출차 · 차단 · OCR 신뢰도: hub_vehicle_logs
@Service
@RequiredArgsConstructor
public class AmrKpiService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<String> BUSY = List.of("TO_PICK", "LOADING", "TO_DROP", "UNLOADING", "TO_RACK", "FORK_WORK");

    private final AmrService amr;
    private final AmrTaskLogRepository taskRepo;
    private final AmrTelemetryRepository telemetryRepo;
    private final AmrRobotRepository robotRepo;
    private final HubVehicleLogRepository vehicleLogRepo;

    @Transactional
    public Map<String, Object> saveTask(Long hubId, TaskRequest req) {
        AmrTaskLog t = new AmrTaskLog();
        t.setHubId(amr.hub(hubId, Access.MANAGE)); t.setRobotCode(req.code() == null ? "-" : req.code());
        t.setTaskType("OUT".equalsIgnoreCase(req.taskType()) ? "OUT" : "IN");
        t.setWaybillNo(req.waybillNo()); t.setBarcode(req.barcode()); t.setDestHub(req.hub()); t.setHandleType(req.handleType());
        t.setWeightKg(req.weightKg()); t.setFromLoc(req.fromLoc()); t.setToLoc(req.toLoc()); t.setInterWaitSec(req.interWaitSec());
        OffsetDateTime end = req.finishedAt() == null ? OffsetDateTime.now() : req.finishedAt();
        t.setStartedAt(req.startedAt()); t.setFinishedAt(end);
        if (req.startedAt() != null) t.setDurationSec(Duration.between(req.startedAt(), end).toMillis() / 1000.0);
        return Map.of("id", taskRepo.save(t).getTaskId());
    }

    @Transactional(readOnly = true)
    public KpiView kpi(Long hubId) {
        Long hub = amr.hub(hubId, Access.VIEW);
        ZonedDateTime now = ZonedDateTime.now(KST);
        OffsetDateTime today0 = now.toLocalDate().atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime yest0 = today0.minusDays(1), yestNow = now.toOffsetDateTime().minusDays(1);

        List<AmrTaskLog> tasks = taskRepo.findByHubIdAndFinishedAtAfter(hub, today0);
        long yesterday = taskRepo.countByHubIdAndFinishedAtBetween(hub, yest0, yestNow);

        // 최근 10시간 시간대별 처리량
        List<HourCount> hourly = new ArrayList<>();
        ZonedDateTime h0 = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).minusHours(9);
        long[] buckets = new long[10];
        for (AmrTaskLog t : taskRepo.findByHubIdAndFinishedAtAfter(hub, h0.toOffsetDateTime())) {
            int k = (int) Duration.between(h0.toOffsetDateTime(), t.getFinishedAt()).toHours();
            if (k >= 0 && k < 10) buckets[k]++;
        }
        for (int i = 0; i < 10; i++) hourly.add(new HourCount(String.format("%02d시", h0.plusHours(i).getHour()), buckets[i]));

        Double avgDur = tasks.stream().map(AmrTaskLog::getDurationSec).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);
        List<Double> waits = tasks.stream().map(AmrTaskLog::getInterWaitSec).filter(w -> w != null && w > 0).toList();
        Double avgWait = waits.stream().mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);

        // 가동률 (최근 1시간)
        Map<Long, String> codes = new HashMap<>();
        for (AmrRobot r : robotRepo.findByHubIdOrderByRobotCodeAsc(hub)) codes.put(r.getRobotId(), r.getRobotCode());
        List<RobotUtil> util = new ArrayList<>();
        for (Object[] row : telemetryRepo.utilization(OffsetDateTime.now().minusHours(1), BUSY)) {
            String code = codes.get((Long) row[0]);
            long all = ((Number) row[1]).longValue(), busy = row[2] == null ? 0 : ((Number) row[2]).longValue();
            if (code != null && all > 0) util.add(new RobotUtil(code, (int) Math.round(busy * 100.0 / all)));
        }
        util.sort(Comparator.comparing(RobotUtil::code));

        List<HubVehicleLog> vlogs = vehicleLogRepo.findByHubIdAndCreatedAtAfterOrderByLogIdAsc(hub, today0);
        long entries = vlogs.stream().filter(v -> "ENTRY".equals(v.getDirection()) && "ALLOW".equals(v.getDecision())).count();
        long exits = vlogs.stream().filter(v -> "EXIT".equals(v.getDirection())).count();
        long blocks = vlogs.stream().filter(v -> "BLOCK".equals(v.getDecision())).count();
        Double ocr = vlogs.stream().map(HubVehicleLog::getOcrConfidence).filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream().boxed().findFirst().orElse(null);

        return new KpiView(tasks.size(), yesterday, hourly, round1(avgDur), round1(avgWait), waits.size(), util,
                entries, exits, blocks, round1(ocr), OffsetDateTime.now());
    }

    private static Double round1(Double v) { return v == null ? null : Math.round(v * 10) / 10.0; }
}
