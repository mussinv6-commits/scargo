package com.scargo.amr.service;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.entity.*;
import com.scargo.amr.repository.*;
import com.scargo.app.entity.Hub; // 통합: amr.entity.Hub → app.entity.Hub (같은 hubs 테이블)
import com.scargo.app.repository.HubRepository;
import com.scargo.amr.security.AmrAccessPolicy;
import com.scargo.amr.security.AmrAccessPolicy.Access;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

// 26.10.14 AMR 연동 서비스
//  - 웹 관제 화면(시뮬레이터)이 센서 · 예측 · 정비 진행을 저장한다
//  - 앱 · 웹 어느 쪽이든 "정비 승인"을 누르면 DB에 남고, 다른 쪽이 2초 안에 읽어서 따라간다
// 26.10.22 허브별 권한: 공개 메서드는 hubId(없으면 기본 · 소속 허브)를 AmrAccessPolicy로 확인한 뒤 그 허브 데이터만 다룬다
//  - 조회(VIEW): ADMIN · CONTROL · HUB_OP(소속) · 허브 앱 / 변경(MANAGE): ADMIN · HUB_OP(소속) · 허브 앱
@Service
@RequiredArgsConstructor
public class AmrService {

    public static final String HUB_CODE = AmrAccessPolicy.DEFAULT_HUB_CODE; // 26.10.22 기본 허브 코드는 AmrAccessPolicy 로 이동
    private static final List<String> OPEN = List.of("SCHEDULED", "IN_PROGRESS");
    private static final Set<String> STATES = Set.of("IDLE", "TO_PICK", "LOADING", "TO_DROP", "UNLOADING",
            "TO_MAINT", "MAINT", "TO_HOME", "TO_RACK", "FORK_WORK", "CHARGING");
    private static final Map<String, String> SIM_STATE = Map.ofEntries(
            Map.entry("idle", "IDLE"),
            Map.entry("toPick", "TO_PICK"),
            Map.entry("load", "LOADING"),
            Map.entry("toDrop", "TO_DROP"),
            Map.entry("unload", "UNLOADING"),
            Map.entry("toMaint", "TO_MAINT"),
            Map.entry("maint", "MAINT"),
            Map.entry("toHome", "TO_HOME"),
            Map.entry("toRack", "TO_RACK"),
            Map.entry("fwork", "FORK_WORK"),
            Map.entry("charge", "CHARGING"));

    private final AmrRobotRepository robotRepo;
    private final AmrTelemetryRepository telemetryRepo;
    private final AmrPredictionRepository predictionRepo;
    private final AmrMaintenanceRepository maintRepo;
    private final HubEventRepository eventRepo;
    private final AmrSettingRepository settingRepo;
    private final HubRepository hubRepo;
    private final HubVehicleLogRepository vehicleLogRepo;
    private final AmrAccessPolicy access;

    // 시뮬레이터가 마지막으로 보낸 값 (진동 · 온도는 amr_robots 에 컬럼이 없어 메모리에 둔다)
    private final Map<String, TelemetryItem> latest = new ConcurrentHashMap<>();
    // 26.10.22 허브별로 따로 둔다 (한 허브의 시뮬레이터가 다른 허브의 단계 · 연결 표시를 바꾸지 않게)
    private final Map<Long, OffsetDateTime> producerAt = new ConcurrentHashMap<>();
    private final Map<Long, Integer> phase = new ConcurrentHashMap<>();

    /** 요청 허브 확인 (권한 범위 밖이면 403) */
    Long hub(Long requestedHubId, Access a) {
        return access.hub(requestedHubId, a);
    }

    /** 26.10.22 통합: 서버 내부 작업(AmrBackgroundSim)용 기본 허브. 로그인 사용자가 없어 권한 확인을 하지 않는다 (같은 패키지에서만 호출) */
    Long internalDefaultHubId() {
        return access.defaultHubId();
    }

    /** 26.10.22 통합: 서버 내부 작업(AmrBackgroundSim)용 허브 운영 기준 (권한 확인 없음) */
    SettingsView internalSettings(Long hub) {
        return settingsView(hub);
    }

    /** 로봇을 찾고, 다룰 수 있는 허브의 로봇인지 확인 */
    private AmrRobot robotInHub(String code, Long hub) {
        AmrRobot r = robot(code);
        if (!hub.equals(r.getHubId())) throw new AccessDeniedException("다른 허브의 로봇입니다: " + code);
        return r;
    }

    /** 정비 대상 로봇의 허브를 다룰 수 있는지 확인하고 그 허브 ID를 돌려준다 */
    private Long maintHub(AmrMaintenance m, Access a) {
        Long hub = robotRepo.findById(m.getRobotId()).map(AmrRobot::getHubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "로봇 없음: " + m.getRobotId()));
        access.requireHub(hub, a);
        return hub;
    }

    private List<Long> robotIds(Long hub) {
        return robotRepo.findByHubIdOrderByRobotCodeAsc(hub).stream().map(AmrRobot::getRobotId).toList();
    }

    /** 예비기 AMR-05 는 같은 허브에 있을 때만 투입 */
    private Optional<AmrRobot> spare(Long hub) {
        return robotRepo.findByRobotCode("AMR-05").filter(sp -> hub.equals(sp.getHubId()));
    }

    private int phaseOf(Long hub) { return phase.getOrDefault(hub, 1); }

    /** 요청에 이름이 없으면 로그인 계정(또는 "허브 앱")으로 기록 */
    private String actor(String requested, String fallback) {
        if (requested != null && !requested.isBlank()) return requested;
        String name = access.actorName();
        return name == null ? fallback : name;
    }

    private AmrRobot robot(String code) {
        return robotRepo.findByRobotCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "로봇 없음: " + code));
    }

    private String codeOf(Long robotId) {
        return robotId == null ? null : robotRepo.findById(robotId).map(AmrRobot::getRobotCode).orElse(null);
    }

    private static String toDbState(String s) {
        if (s == null) return "IDLE";
        String v = SIM_STATE.getOrDefault(s, s.toUpperCase());
        return STATES.contains(v) ? v : "IDLE";
    }

    void event(Long hub, Long robotId, String level, String message) {
        HubEvent e = new HubEvent();
        e.setHubId(hub); e.setRobotId(robotId); e.setEventLevel(level); e.setPhaseNo(phaseOf(hub));
        e.setMessage(message); e.setCreatedAt(OffsetDateTime.now());
        eventRepo.save(e);
    }

    // ── 센서 저장 (2초마다) ──
    @Transactional
    public int saveTelemetry(Long hubId, TelemetryRequest req) {
        Long hub = hub(hubId, Access.MANAGE);
        OffsetDateTime now = OffsetDateTime.now();
        producerAt.put(hub, now);
        if (req.phase() != null) phase.put(hub, req.phase());
        int n = 0;
        for (TelemetryItem it : req.items() == null ? List.<TelemetryItem>of() : req.items()) {
            Optional<AmrRobot> or = robotRepo.findByRobotCode(it.code());
            if (or.isEmpty() || !hub.equals(or.get().getHubId())) continue; // 다른 허브 로봇은 저장하지 않음
            AmrRobot r = or.get();
            String st = toDbState(it.state());
            r.setState(st); r.setBatteryPct(it.battery()); r.setHealthScore(it.health());
            if (it.cumLoadKg() != null) r.setCumLoadKg(it.cumLoadKg());
            r.setUpdatedAt(now);
            AmrTelemetry t = new AmrTelemetry();
            t.setRobotId(r.getRobotId()); t.setVibration(it.vibration()); t.setMotorTemp(it.temp());
            t.setBatteryPct(it.battery()); t.setHealthScore(it.health()); t.setCumLoadKg(it.cumLoadKg());
            t.setSpeed(it.speed()); t.setPosX(it.x()); t.setPosY(it.y()); t.setState(st); t.setRecordedAt(now);
            telemetryRepo.save(t);
            latest.put(it.code(), it);
            n++;
        }
        return n;
    }

    // ── AI 고장 예측 → 정비 예약(승인 대기) ──
    @Transactional
    public MaintView predict(Long hubId, PredictionRequest req) {
        Long hub = hub(hubId, Access.MANAGE);
        AmrRobot r = robotInHub(req.code(), hub);
        Optional<AmrMaintenance> open = maintRepo.findFirstByRobotIdAndStatusInOrderByMaintIdDesc(r.getRobotId(), OPEN);
        if (open.isPresent()) {
            AmrMaintenance old = open.get();
            if (old.getScheduledAt() != null && old.getScheduledAt().isBefore(OffsetDateTime.now().minusMinutes(30))) old.setStatus("CANCELED"); // 오래된 잔여 예약은 정리
            else return view(old); // 진행 중인 예약이 있으면 그대로
        }

        AmrPrediction p = new AmrPrediction();
        p.setRobotId(r.getRobotId()); p.setComponent("구동모터 베어링"); p.setRiskLevel("CRIT");
        p.setPredictedRulHours(req.rulHours()); p.setVibrationSlope(req.slope()); p.setCumLoadKg(req.cumLoadKg());
        p.setReason(req.reason() != null ? req.reason() : "진동 상승 추세 + 누적 운반 중량");
        p.setModelVersion("trend-v1"); p.setResolved(false); p.setPredictedAt(OffsetDateTime.now());
        predictionRepo.save(p);

        AmrMaintenance m = new AmrMaintenance();
        m.setRobotId(r.getRobotId()); m.setPredictionId(p.getPredictionId()); m.setMaintType("PLANNED");
        m.setPartName("구동모터 베어링"); m.setStatus("SCHEDULED"); m.setScheduledAt(OffsetDateTime.now());
        m.setMemo("AI 예측 · 고장까지 약 " + (req.rulHours() == null ? "-" : Math.round(req.rulHours())) + "시간 · 승인 대기");
        maintRepo.save(m);
        phase.put(hub, 3);
        event(hub, r.getRobotId(), "CRIT", r.getRobotCode() + " 고장 예측 · 정비 승인 요청 (약 "
                + (req.rulHours() == null ? "-" : Math.round(req.rulHours())) + "시간)");
        return view(m);
    }

    // ── 관리자 승인 (앱 · 웹 어느 쪽에서든) ──
    @Transactional
    public MaintView approve(Long id, String by) {
        AmrMaintenance m = maintRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "정비 없음: " + id));
        Long hub = maintHub(m, Access.MANAGE);
        if (m.getApprovedAt() == null) {
            m.setApprovedAt(OffsetDateTime.now());
            m.setApprovedBy(actor(by, "관리자"));
            spare(hub).ifPresent(sp -> { m.setSpareRobotId(sp.getRobotId()); sp.setRobotRole("ACTIVE"); });
            event(hub, m.getRobotId(), "WARN", "정비 승인 (" + m.getApprovedBy() + ") · 예비기 AMR-05 투입");
        }
        return view(m);
    }

    // ── 정비 시작 · 완료 (시뮬레이터가 정비 스테이션 도착 · 수리 끝에 호출) ──
    @Transactional
    public MaintView start(Long id) {
        AmrMaintenance m = maintRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long hub = maintHub(m, Access.MANAGE);
        if (!"IN_PROGRESS".equals(m.getStatus()) && !"DONE".equals(m.getStatus())) {
            m.setStatus("IN_PROGRESS"); m.setStartedAt(OffsetDateTime.now()); phase.put(hub, 4);
            event(hub, m.getRobotId(), "CRIT", codeOf(m.getRobotId()) + " 정비 시작 · 구동모터 베어링 교체");
        }
        return view(m);
    }

    @Transactional
    public MaintView done(Long id) {
        AmrMaintenance m = maintRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long hub = maintHub(m, Access.MANAGE);
        if (!"DONE".equals(m.getStatus())) {
            OffsetDateTime now = OffsetDateTime.now();
            m.setStatus("DONE"); m.setCompletedAt(now); phase.put(hub, 5);
            if (m.getPredictionId() != null) predictionRepo.findById(m.getPredictionId()).ifPresent(p -> p.setResolved(true));
            robotRepo.findById(m.getRobotId()).ifPresent(r -> { r.setLastMaintainedAt(now); r.setCumLoadKg(0.0); });
            spare(hub).ifPresent(sp -> sp.setRobotRole("SPARE"));
            event(hub, m.getRobotId(), "OK", codeOf(m.getRobotId()) + " 정비 완료 · 진동 정상 복귀");
        }
        return view(m);
    }

    @Transactional
    public void addEvent(Long hubId, EventRequest req) {
        Long hub = hub(hubId, Access.MANAGE);
        if (req.phase() != null) phase.put(hub, req.phase());
        Long rid = req.code() == null ? null : robotRepo.findByRobotCode(req.code())
                .filter(r -> hub.equals(r.getHubId())).map(AmrRobot::getRobotId).orElse(null);
        String lv = req.level() == null ? "INFO" : req.level().toUpperCase();
        if (!Set.of("INFO", "OK", "WARN", "CRIT", "DISPATCH").contains(lv)) lv = "INFO";
        event(hub, rid, lv, req.message());
    }

    // ── 조회: 앱 · 웹이 2초마다 읽는 현재 상태 ──
    @Transactional(readOnly = true)
    public StateView state(Long hubId) {
        Long hub = hub(hubId, Access.VIEW);
        List<RobotView> robots = robotRepo.findByHubIdOrderByRobotCodeAsc(hub).stream().map(r -> {
            TelemetryItem t = latest.get(r.getRobotCode());
            return new RobotView(r.getRobotCode(), r.getRobotType(), r.getRobotRole(), r.getState(), r.getBatteryPct(),
                    r.getHealthScore(), r.getCumLoadKg(), t == null ? null : t.vibration(), t == null ? null : t.temp(), r.getUpdatedAt());
        }).toList();
        MaintView pending = null, active = null;
        // 30분 넘게 안 끝난 예약(시연용 샘플 · 지난 실행 잔여)은 무시한다
        // 26.10.22 이 허브 로봇의 정비만 (기존: 전체 허브 중 최근 1건)
        List<Long> ids = robotIds(hub);
        Optional<AmrMaintenance> open = (ids.isEmpty() ? Optional.<AmrMaintenance>empty()
                : maintRepo.findFirstByRobotIdInAndStatusInOrderByMaintIdDesc(ids, OPEN))
                .filter(m -> m.getScheduledAt() == null || m.getScheduledAt().isAfter(OffsetDateTime.now().minusMinutes(30)));
        if (open.isPresent()) {
            MaintView v = view(open.get());
            if (v.approvedAt() == null) pending = v; else active = v;
        }
        List<EventView> events = eventRepo.findByHubIdOrderByEventIdDesc(hub, PageRequest.of(0, 20)).stream()
                .map(e -> new EventView(e.getEventId(), codeOf(e.getRobotId()), e.getEventLevel(), e.getPhaseNo(), e.getMessage(), e.getCreatedAt()))
                .toList();
        OffsetDateTime at = producerAt.get(hub);
        boolean live = at != null && at.isAfter(OffsetDateTime.now().minusSeconds(10));
        List<VehicleView> vehicles = vehicleLogRepo.findByHubIdOrderByLogIdDesc(hub, PageRequest.of(0, 8)).stream()
                .map(HubVehicleService::view).toList();
        return new StateView(live, at, phaseOf(hub), robots, pending, active, events, settingsView(hub), vehicles);
    }

    // ── 관리자 운영 기준 (앱 · 웹 어디서 바꿔도 DB 한 곳) ──
    private SettingsView settingsView(Long hub) {
        AmrSetting s = settingRepo.findById(hub).orElseGet(() -> { AmrSetting n = new AmrSetting(); n.setHubId(hub); return n; });
        return new SettingsView(s.getRulThresholdHours(), s.getChargeAtPct(), s.getUpdatedBy(), s.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public SettingsView settings(Long hubId) { return settingsView(hub(hubId, Access.VIEW)); }

    // ── 26.10.22 허브 선택 목록: ADMIN · CONTROL = 전체, HUB_OP · 허브 앱 = 소속 허브 하나 ──
    @Transactional(readOnly = true)
    public List<HubView> hubs() {
        Long only = access.visibleHubId();
        boolean manage = access.canManage();
        List<Hub> list = only == null
                ? hubRepo.findAll(org.springframework.data.domain.Sort.by("hubId"))
                : hubRepo.findById(only).map(List::of).orElse(List.of());
        return list.stream()
                .map(h -> new HubView(h.getHubId(), h.getHubCode(), h.getHubName(), HUB_CODE.equals(h.getHubCode()), manage))
                .toList();
    }

    @Transactional
    public SettingsView updateSettings(Long hubId, SettingsRequest req) {
        Long hub = hub(hubId, Access.MANAGE);
        AmrSetting s = settingRepo.findById(hub).orElseGet(() -> { AmrSetting n = new AmrSetting(); n.setHubId(hub); return n; });
        StringBuilder msg = new StringBuilder();
        if (req.rulThresholdHours() != null) {
            int v = Math.max(6, Math.min(60, req.rulThresholdHours()));
            if (v != s.getRulThresholdHours()) msg.append("정비 기준 ").append(s.getRulThresholdHours()).append("→").append(v).append("시간 ");
            s.setRulThresholdHours(v);
        }
        if (req.chargeAtPct() != null) {
            int v = Math.max(10, Math.min(80, req.chargeAtPct()));
            if (v != s.getChargeAtPct()) msg.append("충전 기준 ").append(s.getChargeAtPct()).append("→").append(v).append("% ");
            s.setChargeAtPct(v);
        }
        s.setUpdatedBy(actor(req.by(), "관리자"));
        s.setUpdatedAt(OffsetDateTime.now());
        settingRepo.save(s);
        if (msg.length() > 0) event(hub, null, "INFO", "운영 기준 변경 (" + s.getUpdatedBy() + ") · " + msg.toString().trim());
        return settingsView(hub);
    }

    @Transactional(readOnly = true)
    public List<SensorPoint> history(Long hubId, String code, int minutes) {
        AmrRobot r = robotInHub(code, hub(hubId, Access.VIEW));
        return telemetryRepo.findByRobotIdAndRecordedAtAfterOrderByRecordedAtAsc(r.getRobotId(),
                        OffsetDateTime.now().minusMinutes(Math.max(1, Math.min(minutes, 120))))
                .stream().map(t -> new SensorPoint(t.getRecordedAt(), t.getVibration(), t.getHealthScore(), t.getState())).toList();
    }

    @Transactional(readOnly = true)
    public List<MaintView> maintenanceList(Long hubId, int limit) {
        List<Long> ids = robotIds(hub(hubId, Access.VIEW));
        if (ids.isEmpty()) return List.of();
        return maintRepo.findByRobotIdInOrderByMaintIdDesc(ids, PageRequest.of(0, Math.max(1, Math.min(limit, 100)))).stream().map(this::view).toList();
    }

    private MaintView view(AmrMaintenance m) {
        Double rul = null, vib = null;
        if (m.getPredictionId() != null) {
            Optional<AmrPrediction> p = predictionRepo.findById(m.getPredictionId());
            if (p.isPresent()) rul = p.get().getPredictedRulHours();
        }
        String code = codeOf(m.getRobotId());
        TelemetryItem t = code == null ? null : latest.get(code);
        if (t != null) vib = t.vibration();
        return new MaintView(m.getMaintId(), code, codeOf(m.getSpareRobotId()), m.getStatus(), m.getPartName(), m.getMemo(),
                rul, vib, m.getScheduledAt(), m.getApprovedAt(), m.getApprovedBy(), m.getStartedAt(), m.getCompletedAt());
    }
}
