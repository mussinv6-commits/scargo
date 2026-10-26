package com.scargo.ops.service;

import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.Enum.NotificationType;
import com.scargo.ops.CarrierLegacy;
import org.springframework.security.access.AccessDeniedException;
import com.scargo.ops.entity.*;
import com.scargo.ops.repository.*;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/** 26.10.20 예외 알림 센터(W-09). DB 설계 확정 규칙:
 *  seal_events: number_matches=FALSE → SEAL_MISMATCH / observed_condition DAMAGED → SEAL_DAMAGED / MISSING → SEAL_LOST
 *  toll_passages: passage_status BLOCKED AND block_reason OVERLOAD → OVERLOAD (center_id NULL, assignment_id 연결)
 *  생성 시 notifications: 과적 OVERLOAD_WARNING(+차량 운송사 company_id), 씰 보류 SEAL_OPEN_HOLD, 그 외 EXCEPTION. reference_id = exception_id
 *  26.10.20 최종: source_key VARCHAR(50), UNIQUE(source_table, source_key, exception_type), 원본 행 존재는 여기서 검증 */
@Service
@RequiredArgsConstructor
public class ExceptionService {
    private final OpsExceptionRepository exceptions;
    private final SealEventRepository sealEvents;
    private final TollPassageRepository tollPassages;
    private final AccountRepository accounts;
    private final ParcelRepository parcels;
    private final ItemRepository items;
    private final BranchUnitRepository units;
    private final ShipmentLegRepository legs;
    private final OpsNotifier notifier;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Value("${scargo.db-notify-triggers:true}") private boolean dbTriggers;

    /** 씰 이벤트 1건 → 해당하는 예외 생성 (없으면 null) */
    @Transactional
    public OpsException fromSealEvent(SealEvent e) {
        String type = null;
        if ("MISSING".equals(e.getObservedCondition())) type = "SEAL_LOST";
        else if ("DAMAGED".equals(e.getObservedCondition())) type = "SEAL_DAMAGED";
        else if (Boolean.FALSE.equals(matches(e))) type = "SEAL_MISMATCH";
        if (type == null) return null;
        String msg = switch (type) {
            case "SEAL_LOST" -> "씰 분실 · 예상 " + e.getExpectedSealNo();
            case "SEAL_DAMAGED" -> "씰 훼손 · " + e.getExpectedSealNo();
            default -> "씰 불일치 (개봉 보류) · 예상 " + e.getExpectedSealNo() + " / 확인 " + e.getObservedSealNo();
        };
        return raise(type, "CRITICAL", e.getCenterId(), null, "seal_events", String.valueOf(e.getEventId()), msg, e.getOccurredAt());
    }

    /** number_matches 는 DB 생성 컬럼. 저장 직후 아직 못 읽은 경우를 위해 같은 식으로 계산 */
    public static Boolean matches(SealEvent e) {
        if (e.getNumberMatches() != null) return e.getNumberMatches();
        return e.getObservedSealNo() == null ? null : e.getObservedSealNo().equals(e.getExpectedSealNo());
    }

    @Transactional
    public void syncFromSources() {
        sealEvents.findAll().forEach(this::fromSealEvent);
        for (TollPassage p : tollPassages.findByPassageStatusAndBlockReason("BLOCKED", "OVERLOAD")) {
            raise("OVERLOAD", "CRITICAL", null, p.getAssignmentId(), "toll_passages", String.valueOf(p.getPassageId()),
                    "과적 차단 · " + (p.getVehicleNo() != null ? p.getVehicleNo() : p.getRecognizedPlateNo()) + " (check_id " + p.getCheckId() + ")", p.getDetectedAt());
        }
    }

    /** 같은 (source_table, source_key, exception_type) 는 한 번만. source_table·source_key 는 둘 다 있거나 둘 다 없음 */
    @Transactional
    public OpsException raise(String type, String severity, Long centerId, Long assignmentId,
                              String sourceTable, String sourceKey, String message, OffsetDateTime at) {
        if (!OpsException.TYPES.contains(type)) throw new IllegalArgumentException("exception_type 값이 아닙니다: " + type);
        // DB chk_exceptions_severity: 씰·과적 예외는 CRITICAL 고정
        if (java.util.Set.of("SEAL_MISMATCH", "SEAL_DAMAGED", "SEAL_LOST", "OVERLOAD").contains(type)) severity = "CRITICAL";
        if ((sourceTable == null) != (sourceKey == null)) throw new IllegalArgumentException("source_table 과 source_key 는 함께 지정해야 합니다.");
        if (sourceTable != null) {
            validateSource(sourceTable, sourceKey);
            if (exceptions.existsBySourceTableAndSourceKeyAndExceptionType(sourceTable, sourceKey, type)) return null;
        }
        OpsException x = new OpsException();
        x.setExceptionType(type); x.setSeverity(severity); x.setCenterId(centerId); x.setAssignmentId(assignmentId);
        x.setSourceTable(sourceTable); x.setSourceKey(sourceKey); x.setMessage(message); x.setStatus("OPEN");
        x.setOccurredAt(at != null ? at : OffsetDateTime.now());
        x = exceptions.save(x);
        notifyException(x);
        return x;
    }

    /** 다형 참조라 FK 가 없으므로 원본 행이 있는지 서비스에서 확인 */
    void validateSource(String table, String key) {
        if (!OpsException.SOURCE_TABLES.contains(table)) throw new IllegalArgumentException("source_table 값이 아닙니다: " + table);
        if (key.length() > 50) throw new IllegalArgumentException("source_key 는 50자 이하입니다.");
        boolean ok;
        try {
            ok = switch (table) {
                case "seal_events" -> sealEvents.existsById(Long.valueOf(key));
                case "toll_passages" -> tollPassages.existsById(Long.valueOf(key));
                case "items" -> items.existsById(Long.valueOf(key));
                case "parcels" -> parcels.existsById(key);
                case "branch_units" -> units.existsById(key);
                case "transport_orders" -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM transport_orders WHERE order_id = ?)", Boolean.class, Long.valueOf(key))); // fix_1300: 정답 씰 미등록
                default -> false;
            };
        } catch (NumberFormatException e) { ok = false; }
        if (!ok) throw new IllegalArgumentException("원본 행이 없습니다: " + table + "/" + key);
    }

    /** 알림. PostgreSQL 트리거와 겹치지 않게:
     *  - 과적: 운송사(company_id) OVERLOAD_WARNING 은 trg_notify_overload_block 이 toll_passages 차단 시 생성 → 서비스는 CONTROL 만
     *  - SEAL_LOST: CONTROL·운송사 EXCEPTION 은 trg_notify_seal_lost 가 생성 → 서비스는 해당 거점 HUB_OP·CENTER_OP 만
     *  H2(트리거 없음)에서는 scargo.db-notify-triggers=false 로 서비스가 같은 알림을 대신 만든다 */
    private void notifyException(OpsException x) {
        String title = ("CRITICAL".equals(x.getSeverity()) ? "[긴급] " : "[경고] ") + x.getExceptionType();
        if ("OVERLOAD".equals(x.getExceptionType())) {
            notifier.toOps(null, NotificationType.OVERLOAD_WARNING, title, x.getMessage(), x.getExceptionId());
            if (!dbTriggers) {
                Long company = tollPassages.findById(Long.valueOf(x.getSourceKey())).map(p -> notifier.companyOfVehicle(p.getVehicleNo())).orElse(null);
                notifier.toCompany(company, NotificationType.OVERLOAD_WARNING, title, x.getMessage(), x.getExceptionId());
            }
            return;
        }
        if ("SEAL_LOST".equals(x.getExceptionType()) && dbTriggers) {
            notifier.toSiteOps(x.getCenterId(), NotificationType.EXCEPTION, title, x.getMessage(), x.getExceptionId());
            return;
        }
        notifier.toOps(x.getCenterId(), NotificationType.EXCEPTION, title, x.getMessage(), x.getExceptionId());
    }

    /** 거점 긴급 여부: PostgreSQL 뷰 v_site_urgency, 없으면(H2) 같은 규칙으로 계산 */
    public List<java.util.Map<String, Object>> siteUrgency() {
        try {
            return jdbc.queryForList("SELECT center_id AS \"centerId\", center_name AS \"centerName\", center_type AS \"centerType\", open_critical_count AS \"openCritical\", open_warning_count AS \"openWarning\", is_urgent AS \"urgent\" FROM v_site_urgency ORDER BY center_id");
        } catch (org.springframework.dao.DataAccessException e) {
            java.util.Map<Long, long[]> m = new java.util.TreeMap<>();
            for (OpsException x : exceptions.findAll()) {
                if (x.getCenterId() == null || "RESOLVED".equals(x.getStatus())) continue;
                long[] c = m.computeIfAbsent(x.getCenterId(), k -> new long[2]);
                c["CRITICAL".equals(x.getSeverity()) ? 0 : 1]++;
            }
            return m.entrySet().stream().map(en -> {
                java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
                r.put("centerId", en.getKey()); r.put("openCritical", en.getValue()[0]); r.put("openWarning", en.getValue()[1]); r.put("urgent", en.getValue()[0] >= 1);
                return r;
            }).toList();
        }
    }

    /** HUB_OP·CENTER_OP 는 소속 거점(center_id)만, 나머지 웹 권한은 전체 */
    @Transactional
    public List<OpsException> list() {
        syncFromSources();
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        return switch (me.getUserType()) {
            case HUB_OP, CENTER_OP -> exceptions.findByCenterIdOrderByOccurredAtDesc(me.getScopeCenterId());
            case ADMIN, CONTROL -> exceptions.findAllByOrderByOccurredAtDesc();
            default -> {
                // 승인 운송사 담당자: 자기 회사 차량(trucks.company_id)의 운행에 걸린 예외만
                if (!CarrierLegacy.isApprovedCarrierContact(me)) throw new AccessDeniedException("승인된 운송사 담당자만 볼 수 있습니다.");
                List<Long> mine = legs.findAll().stream()
                        .filter(l -> Objects.equals(notifier.companyOfVehicle(l.getVehicleNo()), me.getCompanyId()))
                        .map(ShipmentLeg::getAssignmentId).toList();
                // fix_1530: + 우리 회사 컨테이너 오더(transport_orders.company_id)의 씰·반납 예외 (T-02·인계 씰 불일치, 정답 씰 미등록, 반납 기한 등)
                List<Long> orderX = jdbc.queryForList("""
                    SELECT e.exception_id FROM exceptions e
                      LEFT JOIN seal_events v ON e.source_table = 'seal_events' AND e.source_key = v.event_id::text
                      JOIN transport_orders o ON o.order_id = CASE WHEN e.source_table = 'transport_orders' THEN e.source_key::bigint ELSE v.order_id END
                     WHERE e.source_table IN ('seal_events', 'transport_orders') AND o.company_id = ?""", Long.class, me.getCompanyId());
                java.util.Map<Long, OpsException> all = new java.util.LinkedHashMap<>();
                if (!mine.isEmpty()) exceptions.findByAssignmentIdInOrderByOccurredAtDesc(mine).forEach(x -> all.put(x.getExceptionId(), x));
                if (!orderX.isEmpty()) exceptions.findAllById(orderX).forEach(x -> all.put(x.getExceptionId(), x));
                yield all.values().stream().sorted(java.util.Comparator.comparing(OpsException::getOccurredAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))).toList();
            }
        };
    }

    /** OPEN → IN_PROGRESS(담당 = 나) → RESOLVED (resolution_note 필수) */
    @Transactional
    public OpsException advance(Long id, String note) {
        OpsException x = exceptions.findById(id).orElseThrow(() -> new IllegalArgumentException("예외가 없습니다."));
        Account me = accounts.findById(CurrentAccount.id()).orElseThrow();
        if ((me.getUserType() == Account.UserType.HUB_OP || me.getUserType() == Account.UserType.CENTER_OP)
                && !Objects.equals(me.getScopeCenterId(), x.getCenterId())) throw new IllegalStateException("소속 거점의 예외만 처리할 수 있습니다.");
        if ("OPEN".equals(x.getStatus())) {
            x.setStatus("IN_PROGRESS"); x.setAssigneeAccountId(me.getAccountId());
        } else if ("IN_PROGRESS".equals(x.getStatus())) {
            resolve(x, note);
        }
        return x;
    }

    @Transactional
    public void markInProgress(String table, String key, Long assignee) {
        exceptions.findBySourceTableAndSourceKey(table, key).stream().filter(x -> "OPEN".equals(x.getStatus()))
                .forEach(x -> { x.setStatus("IN_PROGRESS"); x.setAssigneeAccountId(assignee); });
    }

    @Transactional
    public void resolveBySource(String table, String key, String note) {
        exceptions.findBySourceTableAndSourceKey(table, key).stream().filter(x -> !"RESOLVED".equals(x.getStatus())).forEach(x -> resolve(x, note));
    }

    private void resolve(OpsException x, String note) {
        if (note == null || note.isBlank()) throw new IllegalArgumentException("처리 내용(resolution_note)을 입력하세요.");
        x.setStatus("RESOLVED"); x.setResolvedAt(OffsetDateTime.now()); x.setResolutionNote(note.trim());
        if (x.getAssigneeAccountId() == null) x.setAssigneeAccountId(CurrentAccount.id());
    }
}
