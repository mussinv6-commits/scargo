package com.scargo.amr.service;

import com.scargo.amr.entity.AmrSafetyAlert;
import com.scargo.amr.entity.AmrSafetyAlertRead;
import com.scargo.amr.repository.AmrSafetyAlertReadRepository;
import com.scargo.amr.repository.AmrSafetyAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 26.11.01 S-Bot AMR 알림 내역 저장 · 조회
 *  표가 없으면 기동/첫 호출 때 DDL 을 한 번 실행한다 (ddl-auto=none 환경).
 */
@Service
public class AmrSafetyAlertService {
    private static final Logger log = LoggerFactory.getLogger(AmrSafetyAlertService.class);
    private static final String[] ZONES = { "D 냉장", "A 인천 랙", "B 서울 랙", "C 경기 랙", "충전소" };

    private final AmrSafetyAlertRepository alerts;
    private final AmrSafetyAlertReadRepository reads;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate txWritable;
    private volatile boolean schemaReady;

    public AmrSafetyAlertService(AmrSafetyAlertRepository alerts, AmrSafetyAlertReadRepository reads,
                                 JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.alerts = alerts;
        this.reads = reads;
        this.jdbc = jdbc;
        // 26.11.02: DDL 은 호출 쪽 readOnly 트랜잭션과 분리 (PG "트랜잭션 중지" 방지)
        this.txWritable = new TransactionTemplate(txManager);
        this.txWritable.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.txWritable.setReadOnly(false);
    }

    /**
     * 26.11.02 fix: list() 는 @Transactional(readOnly=true) 이라 같은 연결에서 CREATE TABLE 하면
     * PostgreSQL 이 트랜잭션을 중지시키고, 이어지는 SELECT 가
     * "현재 트랜잭션은 중지되어 있습니다" 500 을 낸다.
     *  1) 표가 있으면 SELECT 로만 확인하고 schemaReady
     *  2) 없을 때만 REQUIRES_NEW 쓰기 트랜잭션에서 DDL
     */
    public synchronized void ensureSchema() {
        if (schemaReady) return;
        try {
            jdbc.execute("SELECT 1 FROM amr_safety_alerts LIMIT 0");
            jdbc.execute("SELECT 1 FROM amr_safety_alert_reads LIMIT 0");
            schemaReady = true;
            return;
        } catch (Exception existCheck) {
            log.info("amr_safety_alerts 없음 → DDL 시도: {}", existCheck.getMessage());
        }
        try {
            txWritable.executeWithoutResult(status -> {
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS amr_safety_alerts (
                      alert_id BIGSERIAL PRIMARY KEY,
                      event_type VARCHAR(20) NOT NULL,
                      title VARCHAR(200) NOT NULL,
                      message TEXT,
                      equipment VARCHAR(120),
                      zone_label VARCHAR(120),
                      kind VARCHAR(20),
                      code VARCHAR(60),
                      started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                      ended_at TIMESTAMPTZ,
                      duration_sec INTEGER,
                      active BOOLEAN NOT NULL DEFAULT TRUE,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                    )""");
                jdbc.execute("CREATE TABLE IF NOT EXISTS amr_safety_alert_reads (" +
                        "alert_id BIGINT NOT NULL REFERENCES amr_safety_alerts(alert_id) ON DELETE CASCADE," +
                        "account_id BIGINT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE," +
                        "read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (alert_id, account_id))");
            });
            schemaReady = true;
        } catch (Exception e) {
            log.warn("amr_safety_alerts DDL: {}", e.getMessage());
        }
    }

    private String zoneOf(Object zone, String label) {
        if (label != null && !label.isBlank() && !label.matches("(?i)AMR-\\d+")) return label.trim();
        if (zone instanceof Number n) {
            int z = n.intValue();
            if (z >= 0 && z < ZONES.length) return ZONES[z];
        }
        try {
            if (zone != null) {
                int z = Integer.parseInt(String.valueOf(zone));
                if (z >= 0 && z < ZONES.length) return ZONES[z];
            }
        } catch (Exception ignored) { /* keep */ }
        return label == null || label.isBlank() ? "허브" : label;
    }

    private AmrSafetyAlert open(String type, String title, String message, String equipment, String zone, String kind, String code) {
        ensureSchema();
        AmrSafetyAlert a = new AmrSafetyAlert();
        a.setEventType(type);
        a.setTitle(title.startsWith("[긴급]") ? title : "[긴급] " + title);
        a.setMessage(message);
        a.setEquipment(equipment);
        a.setZoneLabel(zone);
        a.setKind(kind);
        a.setCode(code);
        a.setActive(true);
        a.setStartedAt(OffsetDateTime.now());
        return alerts.save(a);
    }

    private void close(AmrSafetyAlert a) {
        if (a == null || !a.isActive()) return;
        OffsetDateTime end = OffsetDateTime.now();
        a.setEndedAt(end);
        a.setActive(false);
        if (a.getStartedAt() != null) {
            a.setDurationSec((int) Math.max(0, Duration.between(a.getStartedAt(), end).getSeconds()));
        }
        alerts.save(a);
    }

    /** 허브/배터리 화재 발령·단계. fresh 허브·batt 또는 batt stage1(온도) */
    @Transactional
    public void onAlert(Map<String, Object> req, boolean fresh) {
        ensureSchema();
        String kind = String.valueOf(req.getOrDefault("kind", "hub"));
        String label = String.valueOf(req.getOrDefault("label", ""));
        String code = req.get("code") != null ? String.valueOf(req.get("code")) : (label.matches("(?i)AMR-\\d+") ? label : null);
        Object zone = req.get("zone");
        int stage = 0;
        try { if (req.get("stage") != null) stage = Integer.parseInt(String.valueOf(req.get("stage"))); } catch (Exception ignored) {}

        if ("batt".equals(kind)) {
            if (stage == 1 || (fresh && stage <= 1)) {
                // 온도 이상 (bfire stage 1)
                Optional<AmrSafetyAlert> openTemp = code != null
                        ? alerts.findFirstByEventTypeAndCodeAndActiveTrueOrderByStartedAtDesc("TEMP", code)
                        : alerts.findFirstByEventTypeAndActiveTrueOrderByStartedAtDesc("TEMP");
                if (openTemp.isEmpty()) {
                    open("TEMP", "AMR 배터리 온도 이상",
                            "작업 중단 · 격리 구역으로 이동 (bfire stage 1)",
                            code != null ? code : "AMR",
                            zoneOf(zone, "이동 중"),
                            kind, code);
                }
                return;
            }
            if (stage >= 2 || fresh) {
                // 진행 중 온도 이상이면 닫고 배터리 화재로 승격
                if (code != null) {
                    alerts.findFirstByEventTypeAndCodeAndActiveTrueOrderByStartedAtDesc("TEMP", code).ifPresent(this::close);
                }
                Optional<AmrSafetyAlert> openBatt = code != null
                        ? alerts.findFirstByEventTypeAndCodeAndActiveTrueOrderByStartedAtDesc("BATT", code)
                        : alerts.findFirstByEventTypeAndActiveTrueOrderByStartedAtDesc("BATT");
                if (openBatt.isEmpty()) {
                    open("BATT", "허브 AMR 배터리 화재",
                            (label == null || label.isBlank() ? "" : label + " · ") + "격리 구역 소화 설비 · 허브 운영 유지",
                            code != null ? code : (label.isBlank() ? "AMR" : label),
                            "배터리 격리 구역",
                            kind, code);
                }
            }
            return;
        }
        // hub fire
        if (fresh) {
            open("FIRE", "허브 화재 발생",
                    "연기 감지 · AMR 대피 · 신규 입차 중단",
                    "허브 전체",
                    zoneOf(zone, label),
                    kind, null);
        }
    }

    @Transactional
    public void onAlertEnd(String kind, String label) {
        ensureSchema();
        if ("batt".equals(kind)) {
            alerts.findByEventTypeAndActiveTrue("BATT").forEach(this::close);
            alerts.findByEventTypeAndActiveTrue("TEMP").forEach(this::close);
            // 종료 알림 행 하나 더 (목록에 종료 제목)
            AmrSafetyAlert end = open("BATT", "허브 AMR 배터리 화재 종료",
                    "화재 상황이 종료되었습니다. 관제 안내에 따라 정상 운행하세요.",
                    label != null && !label.isBlank() ? label : "AMR",
                    "배터리 격리 구역", kind, label);
            close(end); // 즉시 종료 상태로
            end.setTitle("[긴급] 허브 AMR 배터리 화재 종료".replace("[긴급] [긴급] ", "[긴급] "));
            if (!end.getTitle().startsWith("[긴급]")) end.setTitle("[긴급] 허브 AMR 배터리 화재 종료");
            // open() already prefixes; then close sets ended — but title for end event should be 종료 without needing active
            alerts.save(end);
            return;
        }
        alerts.findByEventTypeAndActiveTrue("FIRE").forEach(this::close);
        AmrSafetyAlert end = open("FIRE", "허브 화재 종료",
                "경보 해제 · 장비 작업 복귀",
                "허브 전체",
                label == null || label.isBlank() ? "허브" : label,
                kind, null);
        close(end);
        alerts.save(end);
    }

    /** estop 코드 목록 변경분 → 발령/해제 기록 */
    @Transactional
    public void onEstopChanged(List<String> prev, List<String> next) {
        ensureSchema();
        Set<String> before = new LinkedHashSet<>(prev == null ? List.of() : prev);
        Set<String> after = new LinkedHashSet<>(next == null ? List.of() : next);
        for (String code : after) {
            if (!before.contains(code)) {
                String equip = code == null || code.isBlank() || "all".equalsIgnoreCase(code) ? "AMR 전체" : code;
                open("ESTOP", "AMR 비상 정지",
                        "관제에서 장비 정지 지시 (setEstop)",
                        equip,
                        "허브 전역",
                        "estop",
                        "AMR 전체".equals(equip) ? null : code);
            }
        }
        for (String code : before) {
            if (!after.contains(code)) {
                String key = code;
                alerts.findByEventTypeAndActiveTrue("ESTOP").stream()
                        .filter(a -> Objects.equals(a.getCode(), key) || (key == null && a.getCode() == null)
                                || (a.getEquipment() != null && a.getEquipment().equals(key)))
                        .forEach(a -> {
                            close(a);
                        });
                // 목록용 종료 행
                String equip = code == null || code.isBlank() ? "AMR 전체" : code;
                AmrSafetyAlert end = open("ESTOP", "AMR 비상 정지 해제",
                        "비상 정지 해제 · 작업 재개",
                        equip, "허브 전역", "estop", code);
                close(end);
                alerts.save(end);
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(Long accountId, String type, String read, String q, int page, int size) {
        ensureSchema();
        String typeKey = mapType(type);
        String query = (q == null || q.isBlank()) ? null : q.trim();
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        // 26.11.02: 필터 없으면 단순 조회로 lower(bytea) 바인딩 이슈 회피
        Page<AmrSafetyAlert> pg = (typeKey == null && query == null)
                ? alerts.findAllByOrderByStartedAtDesc(pr)
                : alerts.search(typeKey, query, pr);
        List<AmrSafetyAlert> rows = pg.getContent();
        Set<Long> readIds = accountId == null || rows.isEmpty() ? Set.of()
                : reads.findReadAlertIds(accountId, rows.stream().map(AmrSafetyAlert::getAlertId).toList());
        List<Map<String, Object>> content = new ArrayList<>();
        for (AmrSafetyAlert a : rows) {
            boolean isRead = readIds.contains(a.getAlertId());
            if ("unread".equalsIgnoreCase(read) && isRead) continue;
            if ("read".equalsIgnoreCase(read) && !isRead) continue;
            content.add(toDto(a, isRead));
        }
        // read 필터를 DB에서 안 걸었으면 페이지 카운트가 어긋날 수 있어, read 없을 때는 서버 페이지 사용
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("totalElements", pg.getTotalElements());
        out.put("totalPages", pg.getTotalPages());
        out.put("number", pg.getNumber());
        out.put("size", pg.getSize());
        out.put("summary", summary(accountId));
        return out;
    }

    private Map<String, Object> summary(Long accountId) {
        Map<String, Object> s = new LinkedHashMap<>();
        long all = alerts.count();
        s.put("all", all);
        s.put("fire", alerts.countByEventType("FIRE"));
        s.put("batt", alerts.countByEventType("BATT"));
        s.put("estop", alerts.countByEventType("ESTOP"));
        s.put("temp", alerts.countByEventType("TEMP"));
        long unread = all;
        if (accountId != null && all > 0) {
            long readN = reads.findByAccountId(accountId).size();
            // 대략: 읽은 알림 수 (전체 대비) — 삭제된 알림 읽음은 cascade 로 정리됨
            unread = Math.max(0, all - readN);
        }
        s.put("unread", unread);
        return s;
    }

    private Map<String, Object> toDto(AmrSafetyAlert a, boolean read) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getAlertId());
        m.put("eventType", a.getEventType());
        m.put("type", switch (a.getEventType()) {
            case "FIRE" -> "fire";
            case "BATT" -> "batt";
            case "ESTOP" -> "estop";
            case "TEMP" -> "temp";
            default -> a.getEventType().toLowerCase();
        });
        m.put("title", a.getTitle());
        m.put("message", a.getMessage());
        m.put("equipment", a.getEquipment());
        m.put("zone", a.getZoneLabel());
        m.put("startedAt", a.getStartedAt());
        m.put("endedAt", a.getEndedAt());
        m.put("durationSec", a.getDurationSec() != null ? a.getDurationSec()
                : (a.isActive() && a.getStartedAt() != null
                ? (int) Duration.between(a.getStartedAt(), OffsetDateTime.now()).getSeconds() : null));
        m.put("active", a.isActive());
        m.put("read", read);
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private String mapType(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) return null;
        return switch (type.toLowerCase()) {
            case "fire" -> "FIRE";
            case "batt", "battery" -> "BATT";
            case "estop" -> "ESTOP";
            case "temp" -> "TEMP";
            default -> type.toUpperCase();
        };
    }

    @Transactional
    public void markRead(Long accountId, Long alertId) {
        if (accountId == null || alertId == null) return;
        ensureSchema();
        if (reads.existsByAlertIdAndAccountId(alertId, accountId)) return;
        AmrSafetyAlertRead r = new AmrSafetyAlertRead();
        r.setAlertId(alertId);
        r.setAccountId(accountId);
        r.setReadAt(OffsetDateTime.now());
        reads.save(r);
    }

    @Transactional
    public int markAllRead(Long accountId) {
        if (accountId == null) return 0;
        ensureSchema();
        int n = 0;
        for (AmrSafetyAlert a : alerts.findAll()) {
            if (!reads.existsByAlertIdAndAccountId(a.getAlertId(), accountId)) {
                markRead(accountId, a.getAlertId());
                n++;
            }
        }
        return n;
    }
}