package com.scargo.ops.service;

import com.scargo.app.security.CurrentAccount;
import com.scargo.config.UploadStorage;
import com.scargo.ops.entity.OpsException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 26.11.01 기사앱 추가 메뉴: 사고신고·고객센터·일정·정산·문서함·운행일지·점검이력·설정 */
@Service
@RequiredArgsConstructor
public class AppDriverMenusService {
    private final JdbcTemplate jdbc;
    private final ExceptionService exceptions;
    private final OpsNotifier notifier;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> INC_CAT = Set.of("ACCIDENT","BREAKDOWN","CARGO_DAMAGE","OFF_ROUTE","OTHER");
    private static final Set<String> DOC_TYPE = Set.of("LICENSE","FREIGHT_QUAL","VEHICLE_REG","CARGO_INSURANCE","OTHER");
    private static final Map<String,String> FAQ = new LinkedHashMap<>();
    static {
        FAQ.put("지선 하차 후 인수 확정은 누가 하나요?", "물류센터 관제사가 웹 '센터 인수 확정'에서 처리합니다. 기사 앱에서는 하차 완료까지가 끝입니다.");
        FAQ.put("화재 경보가 울리면 어떻게 하나요?", "안전 구역으로 이동한 뒤 관제 지시에 따르세요. 앱 알림·사이렌은 종료될 때까지 반복될 수 있습니다.");
        FAQ.put("서버에 연결되지 않아요.", "같은 Wi-Fi인지 확인하고, 내 정보 > 서버 주소에서 자동 찾기를 눌러 보세요. PC 방화벽에서 8080 포트가 열려 있어야 합니다.");
        FAQ.put("과적검문소가 너무 많이 안내돼요.", "앱 설정 > 검문소 필터에 안내할 검문소 코드(예: CHECKPOINT-18)만 남겨 두세요. 비우면 전체가 안내됩니다.");
    }

    private Long me() { return CurrentAccount.id(); }
    private String userType() {
        return jdbc.queryForObject("SELECT user_type FROM accounts WHERE account_id = ?", String.class, me());
    }
    private boolean isBranch() { return "BRANCH_DRIVER".equals(userType()); }
    private String myVehicle() {
        List<String> l = jdbc.queryForList("SELECT vehicle_no FROM trucks WHERE assigned_account_id = ? ORDER BY vehicle_no LIMIT 1", String.class, me());
        return l.isEmpty() ? null : l.get(0);
    }
    private static LocalDate parseDay(String s) {
        if (s == null || s.isBlank()) return LocalDate.now(KST);
        return LocalDate.parse(s.trim());
    }
    private static String s(Object o) { return o == null ? null : String.valueOf(o).trim(); }
    private Boolean bool(Object o) { return o == null ? null : Boolean.valueOf(String.valueOf(o)); }
    private Instant toInstant(Object at) {
        if (at instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (at instanceof OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    @Transactional
    public Map<String, Object> reportIncident(Map<String, Object> b, List<MultipartFile> files) throws Exception {
        String cat = s(b.get("category"));
        if (cat == null || !INC_CAT.contains(cat)) throw new IllegalArgumentException("신고 유형을 선택하세요.");
        String sev = "CRITICAL".equalsIgnoreCase(s(b.get("severity"))) ? "CRITICAL" : "WARNING";
        String msg = s(b.get("message"));
        if (msg == null || msg.length() < 5) throw new IllegalArgumentException("상황을 5자 이상 적어 주세요.");
        Double lat = b.get("lat") == null || String.valueOf(b.get("lat")).isBlank() ? null : Double.valueOf(String.valueOf(b.get("lat")));
        Double lng = b.get("lng") == null || String.valueOf(b.get("lng")).isBlank() ? null : Double.valueOf(String.valueOf(b.get("lng")));
        String loc = s(b.get("locationText"));
        Long orderId = b.get("orderId") == null || String.valueOf(b.get("orderId")).isBlank() ? null : Long.valueOf(String.valueOf(b.get("orderId")));
        Long assignmentId = b.get("assignmentId") == null || String.valueOf(b.get("assignmentId")).isBlank() ? null : Long.valueOf(String.valueOf(b.get("assignmentId")));
        String label = switch (cat) {
            case "ACCIDENT" -> "교통사고";
            case "BREAKDOWN" -> "차량고장";
            case "CARGO_DAMAGE" -> "화물파손";
            case "OFF_ROUTE" -> "경로이탈";
            default -> "기타 이상";
        };
        String full = "[기사신고/" + label + "] " + msg
                + (loc != null && !loc.isBlank() ? " · 위치 " + loc : "")
                + (lat != null && lng != null ? String.format(Locale.ROOT, " · GPS %.5f,%.5f", lat, lng) : "");
        OpsException x = exceptions.raise("OTHER", sev, null, assignmentId, null, null, full, OffsetDateTime.now(KST));
        Long exId = x == null ? null : x.getExceptionId();
        Long incidentId = jdbc.queryForObject("""
            INSERT INTO driver_incidents(exception_id, account_id, category, severity, assignment_id, order_id, lat, lng, location_text, message)
            VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING incident_id""", Long.class,
                exId, me(), cat, sev, assignmentId, orderId, lat, lng, loc, msg);
        int photos = 0;
        if (files != null) {
            for (MultipartFile f : files) {
                if (f == null || f.isEmpty()) continue;
                if (f.getSize() > 8L * 1024 * 1024) throw new IllegalArgumentException("사진은 장당 8MB 이하만 올릴 수 있습니다.");
                String path = UploadStorage.save("driver-incidents/" + incidentId, UUID.randomUUID().toString().replace("-", ""), f);
                jdbc.update("INSERT INTO driver_incident_photos(incident_id, file_path, content_type) VALUES (?,?,?)",
                        incidentId, path, f.getContentType());
                photos++;
            }
        }
        if (photos > 4) throw new IllegalArgumentException("사진은 최대 4장까지입니다.");
        String title = ("CRITICAL".equals(sev) ? "[긴급] " : "[경고] ") + "기사 이상 신고 · " + label;
        notifier.toOps(null, com.scargo.Enum.NotificationType.EXCEPTION, title, full, exId);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("incidentId", incidentId); r.put("exceptionId", exId); r.put("photos", photos); r.put("severity", sev); r.put("category", cat);
        return r;
    }

    public List<Map<String, Object>> myIncidents() {
        return jdbc.queryForList("""
            SELECT i.incident_id AS "incidentId", i.exception_id AS "exceptionId", i.category, i.severity, i.message,
                   i.location_text AS "locationText", i.lat, i.lng, i.created_at AS "createdAt",
                   e.status AS "exceptionStatus",
                   (SELECT count(*) FROM driver_incident_photos p WHERE p.incident_id = i.incident_id) AS "photoCount"
              FROM driver_incidents i
              LEFT JOIN exceptions e ON e.exception_id = i.exception_id
             WHERE i.account_id = ?
             ORDER BY i.created_at DESC LIMIT 50""", me());
    }

    public List<Map<String, Object>> myRunsForReport() {
        if (isBranch()) {
            return jdbc.queryForList("""
                SELECT l.assignment_id AS "assignmentId", NULL::bigint AS "orderId",
                       'LH#' || l.assignment_id AS "label", l.status,
                       COALESCE(f.center_name,'허브') || ' → ' || COALESCE(t.center_name,'센터') AS "route"
                  FROM shipment_legs l
                  LEFT JOIN logistics_centers f ON f.center_id = l.from_center_id
                  LEFT JOIN logistics_centers t ON t.center_id = l.to_center_id
                 WHERE l.driver_account_id = ? AND l.status NOT IN ('RECEIVED','CANCELED')
                 ORDER BY l.assignment_id DESC LIMIT 20""", me());
        }
        return jdbc.queryForList("""
            SELECT o.order_id AS "orderId", o.assignment_id AS "assignmentId",
                   COALESCE(o.container_no, o.order_no) AS "label", o.status,
                   COALESCE(tm.terminal_name, '') || ' → ' || COALESCE(hb.hub_name, '') AS "route"
              FROM transport_orders o
              LEFT JOIN terminals tm ON tm.terminal_id = o.origin_terminal_id
              LEFT JOIN hubs hb ON hb.hub_id = o.dest_hub_id
             WHERE o.driver_account_id = ? AND o.status NOT IN ('CANCELED')
             ORDER BY o.order_id DESC LIMIT 20""", me());
    }

    public Map<String, Object> supportHome() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> contacts = new ArrayList<>();
        contacts.add(Map.of("key","control","label","관제실 긴급 (24h)","phone","1588-0000","urgent", true));
        try {
            for (Map<String, Object> h : jdbc.queryForList("""
                SELECT COALESCE(h.hub_name,'허브') AS "label", COALESCE(NULLIF(a.phone_num,''),'031-000-0000') AS "phone"
                  FROM accounts a LEFT JOIN hubs h ON h.hub_id = a.hub_id
                 WHERE a.user_type = 'HUB_OP' AND a.approval_status = 'APPROVED'
                 ORDER BY a.account_id LIMIT 5""")) {
                h.put("key", "hub"); h.put("urgent", false); contacts.add(h);
            }
        } catch (Exception ignored) { contacts.add(Map.of("key","hub","label","곤지암 허브 운영","phone","031-000-0000","urgent", false)); }
        if (contacts.stream().noneMatch(c -> "hub".equals(c.get("key"))))
            contacts.add(Map.of("key","hub","label","곤지암 허브 운영","phone","031-000-0000","urgent", false));
        try {
            for (Map<String, Object> c : jdbc.queryForList("""
                SELECT COALESCE(lc.center_name,'물류센터') AS "label", COALESCE(NULLIF(a.phone_num,''),'032-000-0000') AS "phone"
                  FROM accounts a LEFT JOIN logistics_centers lc ON lc.center_id = a.center_id
                 WHERE a.user_type = 'CENTER_OP' AND a.approval_status = 'APPROVED'
                 ORDER BY a.account_id LIMIT 5""")) {
                c.put("key", "center"); c.put("urgent", false); contacts.add(c);
            }
        } catch (Exception ignored) {}
        contacts.add(Map.of("key","insurance","label","도로공사 / 보험사","phone","1588-2504","urgent", false));
        m.put("contacts", contacts);
        List<Map<String, Object>> faq = new ArrayList<>();
        FAQ.forEach((q,a) -> faq.add(Map.of("q", q, "a", a)));
        m.put("faq", faq);
        m.put("inquiries", jdbc.queryForList("""
            SELECT inquiry_id AS "inquiryId", category, subject, status, created_at AS "createdAt"
              FROM driver_inquiries WHERE account_id = ? ORDER BY created_at DESC LIMIT 20""", me()));
        return m;
    }

    @Transactional
    public Map<String, Object> createInquiry(Map<String, Object> b) {
        String cat = s(b.get("category")); if (cat == null || cat.isBlank()) cat = "OTHER";
        String subject = s(b.get("subject")); String body = s(b.get("body"));
        if (subject == null || subject.length() < 2) throw new IllegalArgumentException("제목을 입력하세요.");
        if (body == null || body.length() < 5) throw new IllegalArgumentException("문의 내용을 5자 이상 적어 주세요.");
        if (subject.length() > 100) throw new IllegalArgumentException("제목은 100자 이하입니다.");
        Long id = jdbc.queryForObject("""
            INSERT INTO driver_inquiries(account_id, category, subject, body) VALUES (?,?,?,?) RETURNING inquiry_id""",
                Long.class, me(), cat, subject, body);
        return Map.of("inquiryId", id, "status", "OPEN");
    }

    public Map<String, Object> schedule(String month) {
        YearMonth ym = (month == null || month.isBlank()) ? YearMonth.now(KST) : YearMonth.parse(month);
        LocalDate from = ym.atDay(1); LocalDate to = ym.atEndOfMonth();
        List<Map<String, Object>> items = new ArrayList<>();
        if (!isBranch()) {
            items.addAll(jdbc.queryForList("""
                SELECT 'CONTAINER' AS "kind", o.order_id AS "id", o.order_no AS "no", o.container_no AS "title", o.status,
                       tm.terminal_name AS "fromName", hb.hub_name AS "toName",
                       COALESCE(o.gate_out_at, o.assigned_at, o.created_at) AS "at",
                       o.arrived_at AS "arrivedAt", o.handed_over_at AS "doneAt"
                  FROM transport_orders o
                  LEFT JOIN terminals tm ON tm.terminal_id = o.origin_terminal_id
                  LEFT JOIN hubs hb ON hb.hub_id = o.dest_hub_id
                 WHERE o.driver_account_id = ? AND o.status <> 'CANCELED'
                   AND COALESCE(o.gate_out_at, o.assigned_at, o.created_at)::date BETWEEN ? AND ?
                 ORDER BY COALESCE(o.gate_out_at, o.assigned_at, o.created_at)""", me(), Date.valueOf(from), Date.valueOf(to)));
        }
        items.addAll(jdbc.queryForList("""
            SELECT 'BRANCH' AS "kind", l.assignment_id AS "id", 'LH#' || l.assignment_id AS "no",
                   COALESCE(l.vehicle_no,'지선') AS "title", l.status,
                   COALESCE(f.center_name,'허브') AS "fromName", COALESCE(t.center_name,'센터') AS "toName",
                   COALESCE(l.departed_at, l.hub_arrived_at, l.created_at) AS "at",
                   l.arrived_at AS "arrivedAt", l.received_at AS "doneAt"
              FROM shipment_legs l
              LEFT JOIN logistics_centers f ON f.center_id = l.from_center_id
              LEFT JOIN logistics_centers t ON t.center_id = l.to_center_id
             WHERE l.driver_account_id = ? AND COALESCE(l.status,'') <> 'CANCELED'
               AND COALESCE(l.departed_at, l.hub_arrived_at, l.created_at)::date BETWEEN ? AND ?
             ORDER BY COALESCE(l.departed_at, l.hub_arrived_at, l.created_at)""", me(), Date.valueOf(from), Date.valueOf(to)));
        items.sort(Comparator.comparing(a -> String.valueOf(a.get("at")), Comparator.nullsLast(String::compareTo)));
        Set<String> days = new TreeSet<>();
        for (Map<String, Object> it : items) {
            Object at = it.get("at");
            if (at instanceof java.sql.Timestamp ts) days.add(ts.toInstant().atZone(KST).toLocalDate().toString());
            else if (at instanceof OffsetDateTime odt) days.add(odt.atZoneSameInstant(KST).toLocalDate().toString());
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("month", ym.toString()); r.put("days", days); r.put("items", items);
        return r;
    }

    public Map<String, Object> settlements(String month) {
        YearMonth ym = (month == null || month.isBlank()) ? YearMonth.now(KST) : YearMonth.parse(month);
        Long mid = me();
        List<Map<String, Object>> tolls = jdbc.queryForList("""
            SELECT tp.passage_id AS "id", COALESCE(tp.passed_at, tp.detected_at) AS "at", tp.fare_won AS "amount",
                   'TOLL' AS "kind", COALESCE(o.order_no, CASE WHEN l.assignment_id IS NOT NULL THEN 'LH#' || l.assignment_id END, tp.vehicle_no) AS "ref",
                   '통행요금' AS "title", 'PAID' AS "status"
              FROM toll_passages tp
              LEFT JOIN transport_orders o ON o.assignment_id = tp.assignment_id AND tp.assignment_id IS NOT NULL
              LEFT JOIN shipment_legs l ON l.assignment_id = tp.assignment_id
             WHERE tp.passage_status = 'PASSED' AND tp.fare_won IS NOT NULL
               AND ( (tp.assignment_id IS NOT NULL AND (o.driver_account_id = ? OR l.driver_account_id = ?))
                  OR (tp.assignment_id IS NULL AND tp.vehicle_no IN (SELECT vehicle_no FROM trucks WHERE assigned_account_id = ?)) )
             ORDER BY COALESCE(tp.passed_at, tp.detected_at) DESC LIMIT 300""", mid, mid, mid);
        List<Map<String, Object>> runs = new ArrayList<>();
        if (!isBranch()) {
            runs.addAll(jdbc.queryForList("""
                SELECT o.order_id AS "id", COALESCE(o.handed_over_at, o.arrived_at) AS "at",
                       CASE WHEN o.status = 'HANDED_OVER' THEN 180000 ELSE 0 END AS "amount",
                       'RUN' AS "kind", o.order_no AS "ref",
                       COALESCE(o.container_no,'운송') || ' 운임(예시단가)' AS "title",
                       CASE WHEN o.status = 'HANDED_OVER' THEN 'PENDING' ELSE 'OPEN' END AS "status"
                  FROM transport_orders o
                 WHERE o.driver_account_id = ? AND o.status IN ('HANDED_OVER','ARRIVED','IN_TRANSIT')
                 ORDER BY COALESCE(o.handed_over_at, o.arrived_at) DESC NULLS LAST LIMIT 100""", mid));
        }
        runs.addAll(jdbc.queryForList("""
            SELECT l.assignment_id AS "id", COALESCE(l.received_at, l.arrived_at) AS "at",
                   CASE WHEN l.status = 'RECEIVED' THEN 85000 ELSE 0 END AS "amount",
                   'BRANCH' AS "kind", 'LH#' || l.assignment_id AS "ref",
                   '지선 수당(예시단가)' AS "title",
                   CASE WHEN l.status = 'RECEIVED' THEN 'PENDING' ELSE 'OPEN' END AS "status"
              FROM shipment_legs l
             WHERE l.driver_account_id = ? AND l.status IN ('RECEIVED','ARRIVED','UNLOADED','IN_TRANSIT','DEPARTED')
             ORDER BY COALESCE(l.received_at, l.arrived_at) DESC NULLS LAST LIMIT 100""", mid));
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(tolls); items.addAll(runs);
        long monthTotal = 0; int monthCount = 0; long pending = 0;
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> r : items) {
            Object at = r.get("at");
            LocalDate d = null;
            if (at instanceof java.sql.Timestamp ts) d = ts.toInstant().atZone(KST).toLocalDate();
            else if (at instanceof OffsetDateTime odt) d = odt.atZoneSameInstant(KST).toLocalDate();
            if (d == null || !YearMonth.from(d).equals(ym)) continue;
            long amt = r.get("amount") == null ? 0L : ((Number) r.get("amount")).longValue();
            monthTotal += amt; monthCount++;
            if ("PENDING".equals(r.get("status"))) pending += amt;
            filtered.add(r);
        }
        filtered.sort((a,b) -> String.valueOf(b.get("at")).compareTo(String.valueOf(a.get("at"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", ym.toString());
        out.put("monthTotal", monthTotal);
        out.put("pendingTotal", pending);
        out.put("monthCount", monthCount);
        out.put("note", "운임·수당은 예시 단가(컨테이너 180,000 / 지선 85,000) + 실제 확정 통행료입니다. 회사 정산 정책 연동 전 미리보기입니다.");
        out.put("items", filtered);
        return out;
    }

    public List<Map<String, Object>> documents() {
        return jdbc.queryForList("""
            SELECT document_id AS "documentId", doc_type AS "docType", title, file_path AS "filePath",
                   expires_at AS "expiresAt", notes, created_at AS "createdAt", updated_at AS "updatedAt",
                   CASE WHEN expires_at IS NULL THEN 'VALID'
                        WHEN expires_at < CURRENT_DATE THEN 'EXPIRED'
                        WHEN expires_at <= CURRENT_DATE + 45 THEN 'SOON' ELSE 'VALID' END AS "status",
                   CASE WHEN expires_at IS NULL THEN NULL ELSE (expires_at - CURRENT_DATE) END AS "daysLeft"
              FROM driver_documents WHERE account_id = ? ORDER BY expires_at NULLS LAST, document_id DESC""", me());
    }

    @Transactional
    public Map<String, Object> upsertDocument(Map<String, Object> b, MultipartFile file) throws Exception {
        String type = s(b.get("docType"));
        if (type == null || !DOC_TYPE.contains(type)) throw new IllegalArgumentException("서류 종류를 선택하세요.");
        String title = s(b.get("title"));
        if (title == null || title.isBlank()) title = switch (type) {
            case "LICENSE" -> "운전면허증";
            case "FREIGHT_QUAL" -> "화물운송종사자격";
            case "VEHICLE_REG" -> "자동차등록증";
            case "CARGO_INSURANCE" -> "적재물배상책임보험";
            default -> "기타 서류";
        };
        Date exp = null;
        String expS = s(b.get("expiresAt"));
        if (expS != null && !expS.isBlank()) exp = Date.valueOf(LocalDate.parse(expS));
        String notes = s(b.get("notes"));
        Long id = b.get("documentId") == null || String.valueOf(b.get("documentId")).isBlank() ? null : Long.valueOf(String.valueOf(b.get("documentId")));
        String path = null;
        if (file != null && !file.isEmpty()) {
            if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("파일은 10MB 이하만 올릴 수 있습니다.");
            path = UploadStorage.save("driver-docs/" + me(), UUID.randomUUID().toString().replace("-", ""), file);
        }
        if (id == null) {
            id = jdbc.queryForObject("""
                INSERT INTO driver_documents(account_id, doc_type, title, file_path, expires_at, notes)
                VALUES (?,?,?,?,?,?) RETURNING document_id""", Long.class, me(), type, title, path, exp, notes);
        } else {
            Integer own = jdbc.queryForObject("SELECT count(*) FROM driver_documents WHERE document_id = ? AND account_id = ?", Integer.class, id, me());
            if (own == null || own == 0) throw new IllegalArgumentException("서류를 찾을 수 없습니다.");
            if (path != null) jdbc.update("UPDATE driver_documents SET doc_type=?, title=?, file_path=?, expires_at=?, notes=?, updated_at=NOW() WHERE document_id=?",
                    type, title, path, exp, notes, id);
            else jdbc.update("UPDATE driver_documents SET doc_type=?, title=?, expires_at=?, notes=?, updated_at=NOW() WHERE document_id=?",
                    type, title, exp, notes, id);
        }
        maybeNotifyExpiry(id);
        return Map.of("documentId", id);
    }

    private void maybeNotifyExpiry(Long documentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT title, expires_at FROM driver_documents WHERE document_id = ? AND expires_at IS NOT NULL
              AND expires_at <= CURRENT_DATE + 45""", documentId);
        if (rows.isEmpty()) return;
        Date exp = (Date) rows.get(0).get("expires_at");
        String title = String.valueOf(rows.get(0).get("title"));
        long days = ChronoUnit.DAYS.between(LocalDate.now(KST), exp.toLocalDate());
        String msg = days < 0 ? title + "이(가) 만료되었습니다." : title + " 만료까지 " + days + "일 남았습니다.";
        jdbc.update("""
            INSERT INTO notifications(account_id, title, message, notification_type, reference_id)
            VALUES (?,?,?, 'EXCEPTION', ?)""", me(), days < 0 ? "[긴급] 서류 만료" : "[경고] 서류 만료 임박", msg, documentId);
    }

    @Transactional
    public void deleteDocument(Long id) {
        int n = jdbc.update("DELETE FROM driver_documents WHERE document_id = ? AND account_id = ?", id, me());
        if (n == 0) throw new IllegalArgumentException("서류를 찾을 수 없습니다.");
    }

    public Map<String, Object> logbook(String day) {
        LocalDate d = parseDay(day);
        List<Map<String, Object>> events = new ArrayList<>();
        if (!isBranch()) {
            events.addAll(jdbc.queryForList("""
                SELECT 'CONTAINER' AS "kind", o.order_no AS "ref", o.status,
                       o.gate_out_at AS "startAt", o.arrived_at AS "endAt",
                       tm.terminal_name AS "fromName", hb.hub_name AS "toName",
                       o.planned_distance_km AS "distanceKm"
                  FROM transport_orders o
                  LEFT JOIN terminals tm ON tm.terminal_id = o.origin_terminal_id
                  LEFT JOIN hubs hb ON hb.hub_id = o.dest_hub_id
                 WHERE o.driver_account_id = ?
                   AND (o.gate_out_at::date = ? OR o.arrived_at::date = ? OR o.handed_over_at::date = ? OR o.assigned_at::date = ?)
                 ORDER BY o.gate_out_at NULLS LAST""", me(), Date.valueOf(d), Date.valueOf(d), Date.valueOf(d), Date.valueOf(d)));
        }
        events.addAll(jdbc.queryForList("""
            SELECT 'BRANCH' AS "kind", 'LH#' || l.assignment_id AS "ref", l.status,
                   l.departed_at AS "startAt", COALESCE(l.arrived_at, l.received_at) AS "endAt",
                   COALESCE(f.center_name,'허브') AS "fromName", COALESCE(t.center_name,'센터') AS "toName",
                   NULL::numeric AS "distanceKm"
              FROM shipment_legs l
              LEFT JOIN logistics_centers f ON f.center_id = l.from_center_id
              LEFT JOIN logistics_centers t ON t.center_id = l.to_center_id
             WHERE l.driver_account_id = ?
               AND (l.departed_at::date = ? OR l.arrived_at::date = ? OR l.received_at::date = ?)
             ORDER BY l.departed_at NULLS LAST""", me(), Date.valueOf(d), Date.valueOf(d), Date.valueOf(d)));
        long driveMin = 0; double dist = 0;
        List<Map<String, Object>> timeline = new ArrayList<>();
        for (Map<String, Object> e : events) {
            Object sAt = e.get("startAt"), eAt = e.get("endAt");
            Instant sI = toInstant(sAt), eI = toInstant(eAt);
            if (sI != null && eI != null && eI.isAfter(sI)) driveMin += Duration.between(sI, eI).toMinutes();
            if (e.get("distanceKm") instanceof Number n) dist += n.doubleValue();
            if (sI != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("at", sAt); t.put("label", e.get("ref") + " 출발"); t.put("place", Objects.toString(e.get("fromName"), ""));
                timeline.add(t);
            }
            if (eI != null) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("at", eAt); t.put("label", e.get("ref") + " 도착/완료"); t.put("place", Objects.toString(e.get("toName"), ""));
                timeline.add(t);
            }
        }
        timeline.sort((a,b) -> String.valueOf(a.get("at")).compareTo(String.valueOf(b.get("at"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("day", d.toString());
        out.put("driveMinutes", driveMin);
        out.put("restMinutes", driveMin >= 120 ? 20 : 0);
        out.put("distanceKm", Math.round(dist * 10) / 10.0);
        out.put("status", driveMin > 0 ? "DRIVING" : "OFF");
        out.put("events", events);
        out.put("timeline", timeline);
        out.put("note", "운행·휴게는 배차 출발/도착 시각으로 계산한 요약입니다. 법정 근무·휴게 기록계와는 별도입니다.");
        return out;
    }

    public Map<String, Object> nearbyAids(Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lat", lat); m.put("lng", lng);
        try {
            m.put("checkpoints", jdbc.queryForList("""
                SELECT station_id AS "id", station_name AS "name", station_code AS "code",
                       latitude AS "lat", longitude AS "lng", 'CHECKPOINT' AS "type"
                  FROM weigh_stations WHERE COALESCE(is_active, TRUE) ORDER BY station_id"""));
        } catch (Exception e) { m.put("checkpoints", List.of()); }
        try {
            m.put("depots", jdbc.queryForList("""
                SELECT h.hub_id AS "id", h.hub_name AS "name",
                       c.latitude AS "lat", c.longitude AS "lng", 'DEPOT' AS "type"
                  FROM hubs h LEFT JOIN logistics_centers c ON c.center_id = h.center_id
                 ORDER BY h.hub_id"""));
        } catch (Exception e) { m.put("depots", List.of()); }
        List<Map<String, Object>> rest = new ArrayList<>();
        rest.add(Map.of("id","rest-unyang","name","언양휴게소 (서울방향)","type","REST","lat",35.569,"lng",129.126,"amenities","대형차 주차 · 식당 · 샤워","example", true));
        rest.add(Map.of("id","fuel-giheung","name","알뜰주유 기흥IC","type","FUEL","lat",37.237,"lng",127.104,"amenities","화물차 전용 · 24h","example", true));
        m.put("restFuel", rest);
        m.put("note", "휴게소·주유소 일부는 예시 POI입니다. 과적검문소·허브 차고는 DB 실데이터입니다.");
        return m;
    }

    public Map<String, Object> inspectionHistory(String range, String result) {
        String r = range == null || range.isBlank() ? "month" : range;
        LocalDate to = LocalDate.now(KST);
        LocalDate from = switch (r) {
            case "today" -> to;
            case "week" -> to.minusDays(6);
            default -> to.minusDays(30);
        };
        String veh = myVehicle();
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(jdbc.queryForList("""
            SELECT inspection_id AS "id", 'VEHICLE' AS "kind", vehicle_no AS "ref", overall_status AS "overall",
                   inspection_date AS "day", created_at AS "at", memo,
                   light_status AS "light", brake_status AS "brake", air_brake_status AS "airBrake",
                   tire_status AS "tire", steering_status AS "steering", cargo_securement_status AS "securement",
                   engine_oil_status AS "engineOil", seatbelt_status AS "seatbelt",
                   fire_extinguisher_status AS "extinguisher", safety_triangle_status AS "triangle"
              FROM vehicle_checklist
             WHERE inspection_date BETWEEN ? AND ?
               AND (inspector_account_id = ? OR vehicle_no = ?)
             ORDER BY created_at DESC""", Date.valueOf(from), Date.valueOf(to), me(), veh == null ? "" : veh));
        if (!isBranch()) {
            items.addAll(jdbc.queryForList("""
                SELECT inspection_id AS "id", 'CONTAINER' AS "kind", container_no AS "ref", overall_status AS "overall",
                       created_at::date AS "day", created_at AS "at", NULL AS memo
                  FROM container_inspections
                 WHERE driver_account_id = ? AND COALESCE(record_status,'') <> 'CANCELLED'
                   AND created_at::date BETWEEN ? AND ?
                 ORDER BY created_at DESC""", me(), Date.valueOf(from), Date.valueOf(to)));
        }
        if (result != null && !result.isBlank() && !"ALL".equalsIgnoreCase(result)) {
            String want = result.trim().toUpperCase(Locale.ROOT);
            boolean wantFail = "FAIL".equals(want) || "불량".equals(result);
            boolean wantWarn = "WARN".equals(want) || "CAUTION".equals(want) || "주의".equals(result);
            boolean wantPass = "PASS".equals(want) || "OK".equals(want) || "양호".equals(result);
            items.removeIf(it -> {
                String o = String.valueOf(it.get("overall")).toUpperCase(Locale.ROOT);
                boolean fail = o.contains("FAIL") || o.contains("BAD") || o.contains("NG") || o.contains("불량");
                boolean warn = !fail && (o.contains("WARN") || o.contains("CAUTION") || o.contains("주의"));
                boolean pass = !fail && !warn;
                if (wantFail) return !fail;
                if (wantWarn) return !warn;
                if (wantPass) return !pass;
                return false;
            });
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("range", r); out.put("from", from.toString()); out.put("to", to.toString());
        out.put("vehicleNo", veh); out.put("branch", isBranch());
        out.put("items", items);
        out.put("note", isBranch()
                ? "지선 기사는 차량 점검표 결과만 표시합니다. 정비소 수리·비용 기록은 포함되지 않습니다."
                : "컨테이너 기사는 차량 점검표 + 컨테이너 점검표 결과를 날짜별로 보여 줍니다. 정비소 수리 이력은 없습니다.");
        return out;
    }

    public Map<String, Object> getSettings() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM driver_app_settings WHERE account_id = ?", me());
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO driver_app_settings(account_id) VALUES (?) ON CONFLICT (account_id) DO NOTHING", me());
            rows = jdbc.queryForList("SELECT * FROM driver_app_settings WHERE account_id = ?", me());
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ttsEnabled", row.get("tts_enabled"));
        m.put("ttsVolume", row.get("tts_volume"));
        m.put("ttsRate", row.get("tts_rate"));
        m.put("alertDispatch", row.get("alert_dispatch"));
        m.put("alertException", row.get("alert_exception"));
        m.put("alertFire", row.get("alert_fire"));
        m.put("alertNotice", row.get("alert_notice"));
        m.put("checkpointAllow", row.get("checkpoint_allow"));
        m.put("largeText", row.get("large_text"));
        return m;
    }

    @Transactional
    public Map<String, Object> saveSettings(Map<String, Object> b) {
        getSettings();
        Boolean tts = b.get("ttsEnabled") == null ? null : Boolean.valueOf(String.valueOf(b.get("ttsEnabled")));
        BigDecimal vol = b.get("ttsVolume") == null ? null : new BigDecimal(String.valueOf(b.get("ttsVolume")));
        BigDecimal rate = b.get("ttsRate") == null ? null : new BigDecimal(String.valueOf(b.get("ttsRate")));
        if (vol != null && (vol.doubleValue() < 0 || vol.doubleValue() > 1)) throw new IllegalArgumentException("음량은 0~1 사이입니다.");
        if (rate != null && (rate.doubleValue() < 0.5 || rate.doubleValue() > 2.0)) throw new IllegalArgumentException("속도는 0.5~2.0 사이입니다.");
        String cp = b.containsKey("checkpointAllow") ? Objects.toString(b.get("checkpointAllow"), "") : null;
        jdbc.update("""
            UPDATE driver_app_settings SET
              tts_enabled = COALESCE(?, tts_enabled),
              tts_volume = COALESCE(?, tts_volume),
              tts_rate = COALESCE(?, tts_rate),
              alert_dispatch = COALESCE(?, alert_dispatch),
              alert_exception = COALESCE(?, alert_exception),
              alert_fire = COALESCE(?, alert_fire),
              alert_notice = COALESCE(?, alert_notice),
              checkpoint_allow = COALESCE(?, checkpoint_allow),
              large_text = COALESCE(?, large_text),
              updated_at = NOW()
             WHERE account_id = ?""",
                tts, vol, rate,
                bool(b.get("alertDispatch")), bool(b.get("alertException")), bool(b.get("alertFire")), bool(b.get("alertNotice")),
                cp, bool(b.get("largeText")), me());
        return getSettings();
    }
}

