package com.scargo.service;

import com.scargo.dto.*;
import com.scargo.entity.Account;
import com.scargo.entity.Account.UserType;
import com.scargo.entity.ContainerInspection;
import com.scargo.entity.ContainerInspectionAudit;
import com.scargo.entity.ContainerInspectionAudit.Action;
import com.scargo.repository.ContainerInspectionAuditRepository;
import com.scargo.repository.ContainerInspectionRepository;
import com.scargo.repository.ContainerRepository;
import com.scargo.security.ContainerInspectionAccessPolicy;
import com.scargo.security.ContainerInspectionAccessPolicy.OrderInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

import static com.scargo.security.ContainerInspectionAccessPolicy.is;

/**
 * 26.10.23 컨테이너 점검표
 *
 * 역할별 권한 (조회 범위는 ContainerInspectionAccessPolicy)
 *   ADMIN               생성 · DRAFT 수정/확정 · 취소(DRAFT, FINALIZED 는 사유 + 감사 기록 예외 절차) · 재점검
 *   CONTROL             조회만
 *   CORPORATE_APPROVED  자기 업체: 생성 · DRAFT 수정/확정 · DRAFT 취소 · 재점검
 *   CONTAINER_DRIVER    본인: 생성 · 본인 DRAFT 수정/확정 · 재점검 (취소 불가)
 *   HUB_OP              소속 허브 도착 확인 뒤 인계 전: 생성(검증된 오더 필수) · 수정/확정 · DRAFT 취소 · 재점검
 *
 * 규칙
 *   - 판정은 서버가 items 로 다시 계산 (7개 모두 필수, 미완 PENDING · 모두 양호 PASS · 하나라도 불량 FAIL)
 *   - 업체 · 허브는 로그인 계정과 검증된 오더 연결로만 정함 (요청 값 없음)
 *   - 오더 자동 연결: 진행 중 오더(ASSIGNED ~ ARRIVED)가 정확히 1건일 때만, 아니면 NULL
 *   - 확정본은 바꾸지 않음. 재점검은 새 행 + 직전 기록 연결 (체인 마지막 확정본에서만, 동시에 1개)
 *   - 상태 변경과 감사 기록은 같은 트랜잭션. 중복 DRAFT · 동시 재점검 등 DB 제약 위반은 409
 *   - transport_orders.precheck 는 쓰지 않음
 */
@Service
@RequiredArgsConstructor
public class ContainerInspectionService {

    private final ContainerInspectionRepository inspectionRepository;
    private final ContainerInspectionAuditRepository auditRepository;
    private final ContainerRepository containerRepository;
    private final ContainerInspectionAccessPolicy policy;

    // ── 조회 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContainerInspectionResponse> list(Authentication authentication, String containerNo, Long orderId, String status) {
        Account actor = policy.actor(authentication);
        List<Long> ids = policy.visibleIds(actor, containerNo, orderId, status);
        Map<Long, ContainerInspection> byId = new HashMap<>();
        inspectionRepository.findAllById(ids).forEach(c -> byId.put(c.getInspectionId(), c));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(c -> response(actor, c)).toList();
    }

    @Transactional(readOnly = true)
    public ContainerInspectionResponse get(Authentication authentication, Long id) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(id);
        policy.requireView(actor, c);
        return response(actor, c);
    }

    /** 재점검 체인 전체(처음 기록 ~ 마지막 기록)의 변경 이력. 볼 수 있는 기록의 이력만 */
    @Transactional(readOnly = true)
    public List<ContainerInspectionAuditResponse> history(Authentication authentication, Long id) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = find(id);
        policy.requireView(actor, c);
        List<Long> chain = chain(c).stream().filter(x -> policy.canView(actor, x)).map(ContainerInspection::getInspectionId).toList();
        return auditRepository.findByInspectionIdInOrderByCreatedAtAscAuditIdAsc(chain).stream()
                .map(ContainerInspectionAuditResponse::new).toList();
    }

    // ── 작성 ─────────────────────────────────────────────

    @Transactional
    public ContainerInspectionResponse create(Authentication authentication, ContainerInspectionCreateRequest request) {
        Account actor = policy.actor(authentication);
        requireWriter(actor);
        String containerNo = request.getContainerNo().trim().toUpperCase();
        if (!containerRepository.existsById(containerNo)) {
            throw new IllegalArgumentException("존재하지 않는 컨테이너입니다: " + containerNo);
        }

        OrderInfo order = resolveOrder(actor, containerNo, request.getOrderId());
        if (order == null) {
            // 오더 없이 만들 때 컨테이너 검증
            if (is(actor, UserType.CONTAINER_DRIVER) && !policy.driverOwnsContainer(actor, containerNo)) {
                throw new AccessDeniedException("본인 배정 차량에 매핑된 컨테이너만 점검할 수 있습니다.");
            }
            if (is(actor, UserType.CORPORATE_APPROVED) && !policy.companyOwnsContainer(actor, containerNo)) {
                throw new AccessDeniedException("소속 업체의 컨테이너만 점검할 수 있습니다.");
            }
        }

        Map<String, Boolean> items = validItems(request.getItems());
        ContainerInspection c = ContainerInspection.builder()
                .containerNo(containerNo)
                .orderId(order == null ? null : order.orderId())
                .companyId(order != null ? order.companyId() : (is(actor, UserType.ADMIN) ? null : actor.getCompanyId()))
                .hubId(order == null ? null : order.destHubId())
                .driverAccountId(is(actor, UserType.CONTAINER_DRIVER) ? actor.getAccountId() : null)
                .createdByAccountId(actor.getAccountId())
                .items(items)
                .overallStatus(ContainerInspection.judge(items))
                .memo(blankToNull(request.getMemo()))
                .recordStatus(ContainerInspection.DRAFT)
                .createdAt(OffsetDateTime.now())
                .build();
        c = save(c);
        audit(c, Action.CREATE, actor, null, null);
        return response(actor, c);
    }

    // 연결할 오더 결정 (R2 · K4) + 역할별 오더 범위
    private OrderInfo resolveOrder(Account actor, String containerNo, Long requestedOrderId) {
        List<Long> linkable = policy.linkableOrderIds(containerNo);
        OrderInfo order;
        if (requestedOrderId != null) {
            order = policy.order(requestedOrderId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 오더입니다: " + requestedOrderId));
            if (!containerNo.equals(order.containerNo())) {
                throw new IllegalArgumentException("오더의 컨테이너(" + order.containerNo() + ")와 점검 컨테이너가 다릅니다.");
            }
            if (!ContainerInspectionAccessPolicy.LINKABLE_ORDER_STATUSES.contains(order.status())) {
                throw new IllegalStateException("진행 중인 오더만 연결할 수 있습니다 (현재 " + order.status() + ")");
            }
        } else if (linkable.size() == 1) {
            order = policy.order(linkable.get(0)).orElseThrow();
        } else {
            order = null;
        }

        if (is(actor, UserType.HUB_OP)) {
            // K4: 검증된 오더 연결 필수. 자격 있는 오더가 정확히 1건이어야 함
            if (linkable.size() != 1 || order == null || !order.orderId().equals(linkable.get(0))) {
                throw new IllegalStateException("허브 관제사는 진행 중 오더가 정확히 1건인 컨테이너만 점검할 수 있습니다 (현재 "
                        + linkable.size() + "건)");
            }
            if (!actor.getHubId().equals(order.destHubId())) {
                throw new AccessDeniedException("소속 허브로 오는 오더가 아닙니다.");
            }
            policy.requireHubWork(actor, order.orderId());
            return order;
        }
        if (order == null) return null;
        if (is(actor, UserType.CONTAINER_DRIVER)) {
            if (!order.companyId().equals(actor.getCompanyId())) {
                throw new IllegalStateException("연결된 오더의 운송사와 기사 소속 업체가 다릅니다.");
            }
            // 26.10.23 배정 기사 · 차량까지 확인 (같은 업체의 다른 기사 오더에는 점검을 연결하지 못함)
            if (!policy.driverOwnsOrder(actor, order)) {
                throw new AccessDeniedException("본인에게 배정되고 본인 차량으로 운송하는 오더만 점검할 수 있습니다: " + order.orderNo());
            }
        }
        if (is(actor, UserType.CORPORATE_APPROVED) && !order.companyId().equals(actor.getCompanyId())) {
            throw new IllegalStateException("연결된 오더가 소속 업체의 오더가 아닙니다.");
        }
        return order;
    }

    // ── 수정 · 확정 ─────────────────────────────────────────

    @Transactional
    public ContainerInspectionResponse update(Authentication authentication, Long id, ContainerInspectionUpdateRequest request) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = lock(id);
        requireEdit(actor, c);
        Map<String, Object> before = snapshot(c);
        if (request.getItems() != null) {
            Map<String, Boolean> items = validItems(request.getItems());
            c.setItems(items);
            c.setOverallStatus(ContainerInspection.judge(items));
        }
        if (request.getMemo() != null) {
            c.setMemo(blankToNull(request.getMemo()));
        }
        c = save(c);
        audit(c, Action.UPDATE, actor, before, null);
        return response(actor, c);
    }

    @Transactional
    public ContainerInspectionResponse finalizeInspection(Authentication authentication, Long id) {
        Account actor = policy.actor(authentication);
        ContainerInspection c = lock(id);
        requireEdit(actor, c);
        // 판정은 저장된 항목으로 다시 계산
        String judged = ContainerInspection.judge(c.getItems());
        if (ContainerInspection.PENDING.equals(judged)) {
            throw new IllegalStateException("점검 항목 7개를 모두 입력해야 확정할 수 있습니다.");
        }
        Map<String, Object> before = snapshot(c);
        c.setOverallStatus(judged);
        c.setRecordStatus(ContainerInspection.FINALIZED);
        c.setFinalizedAt(OffsetDateTime.now());
        c.setFinalizedByAccountId(actor.getAccountId());
        c = save(c);
        audit(c, Action.FINALIZE, actor, before, null);
        return response(actor, c);
    }

    // ── 취소 ─────────────────────────────────────────────

    @Transactional
    public ContainerInspectionResponse cancel(Authentication authentication, Long id, ContainerInspectionCancelRequest request) {
        Account actor = policy.actor(authentication);
        String reason = request.getReason().trim();
        ContainerInspection c = lock(id);
        policy.requireView(actor, c);
        if (is(actor, UserType.CONTROL) || is(actor, UserType.CONTAINER_DRIVER)) {
            throw new AccessDeniedException("점검 기록을 취소할 권한이 없습니다.");
        }
        if (ContainerInspection.CANCELLED.equals(c.getRecordStatus())) {
            throw new IllegalStateException("이미 취소된 점검 기록입니다.");
        }
        if (c.isFinalized()) {
            // 확정본 취소는 관리자 예외 절차 (사유 + 감사 기록)
            if (!is(actor, UserType.ADMIN)) {
                throw new AccessDeniedException("확정된 점검 기록은 관리자(ADMIN)만 취소할 수 있습니다.");
            }
        } else {
            requireDraftCancel(actor, c);
        }
        Map<String, Object> before = snapshot(c);
        c.setRecordStatus(ContainerInspection.CANCELLED);
        c.setCancelledAt(OffsetDateTime.now());
        c.setCancelReason(reason);
        c.setCancelledByAccountId(actor.getAccountId());
        c = save(c);
        audit(c, Action.CANCEL, actor, before, reason);
        return response(actor, c);
    }

    // ── 재점검 ────────────────────────────────────────────

    @Transactional
    public ContainerInspectionResponse reinspect(Authentication authentication, Long id, ContainerInspectionReinspectRequest request) {
        Account actor = policy.actor(authentication);
        ContainerInspection previous = lock(id);
        requireReinspect(actor, previous);
        if (!previous.isFinalized()) {
            throw new IllegalStateException("확정된 점검 기록만 재점검할 수 있습니다.");
        }
        if (inspectionRepository.existsByPreviousInspectionIdAndRecordStatusNot(id, ContainerInspection.CANCELLED)) {
            throw new IllegalStateException("이미 이 기록의 재점검이 있습니다. 체인의 마지막 확정 기록에서 재점검하세요.");
        }
        if (previous.getContainerNo() == null) {
            throw new IllegalStateException("컨테이너 정보가 없는 기록은 재점검할 수 없습니다.");
        }

        Map<String, Boolean> items = validItems(request.getItems());
        ContainerInspection c = ContainerInspection.builder()
                .containerNo(previous.getContainerNo())
                .orderId(previous.getOrderId())
                .companyId(previous.getCompanyId())
                .hubId(previous.getHubId())
                .driverAccountId(is(actor, UserType.CONTAINER_DRIVER) ? actor.getAccountId() : null)
                .createdByAccountId(actor.getAccountId())
                .previousInspectionId(previous.getInspectionId())
                .items(items)
                .overallStatus(ContainerInspection.judge(items))
                .memo(blankToNull(request.getMemo()))
                .recordStatus(ContainerInspection.DRAFT)
                .createdAt(OffsetDateTime.now())
                .build();
        c = save(c);
        audit(c, Action.REINSPECT, actor, snapshot(previous), blankToNull(request.getReason()));
        return response(actor, c);
    }

    // ── 권한 판단 ─────────────────────────────────────────

    private static void requireWriter(Account actor) {
        if (is(actor, UserType.CONTROL)) {
            throw new AccessDeniedException("통합 관제사는 점검표를 조회만 할 수 있습니다.");
        }
    }

    // DRAFT 수정 · 확정. 순서: 조회 범위(403) → 역할 · 허브 업무 단계(403) → 기록 상태(409)
    private void requireEdit(Account actor, ContainerInspection c) {
        policy.requireView(actor, c);
        switch (actor.getUserType()) {
            case ADMIN, CORPORATE_APPROVED, CONTAINER_DRIVER -> { } // 조회 범위 = 수정 범위 (기사는 본인 기록)
            case HUB_OP -> policy.requireHubWork(actor, c.getOrderId());
            default -> throw new AccessDeniedException("점검 기록을 수정할 권한이 없습니다.");
        }
        if (!c.isDraft()) {
            throw new IllegalStateException("작성 중(DRAFT)인 점검 기록만 바꿀 수 있습니다 (현재 " + c.getRecordStatus() + ")");
        }
    }

    private void requireDraftCancel(Account actor, ContainerInspection c) {
        switch (actor.getUserType()) {
            case ADMIN, CORPORATE_APPROVED -> { }
            case HUB_OP -> policy.requireHubWork(actor, c.getOrderId());
            default -> throw new AccessDeniedException("점검 기록을 취소할 권한이 없습니다.");
        }
    }

    private void requireReinspect(Account actor, ContainerInspection previous) {
        policy.requireView(actor, previous);
        switch (actor.getUserType()) {
            case ADMIN, CORPORATE_APPROVED, CONTAINER_DRIVER -> { }
            case HUB_OP -> policy.requireHubWork(actor, previous.getOrderId());
            default -> throw new AccessDeniedException("재점검 권한이 없습니다.");
        }
    }

    private ContainerInspectionResponse response(Account actor, ContainerInspection c) {
        UserType type = actor.getUserType();
        boolean manager = type == UserType.ADMIN || type == UserType.CORPORATE_APPROVED || type == UserType.CONTAINER_DRIVER;
        boolean hubWork = type == UserType.HUB_OP && policy.hubWorkAllowed(actor, c.getOrderId());
        boolean canEdit = c.isDraft() && (manager || hubWork);
        boolean canCancel = (c.isDraft() && (type == UserType.ADMIN || type == UserType.CORPORATE_APPROVED || hubWork))
                || (c.isFinalized() && type == UserType.ADMIN);
        boolean canReinspect = c.isFinalized() && c.getContainerNo() != null && (manager || hubWork)
                && !inspectionRepository.existsByPreviousInspectionIdAndRecordStatusNot(c.getInspectionId(), ContainerInspection.CANCELLED);
        return ContainerInspectionResponse.from(c)
                .orderNo(policy.orderNo(c.getOrderId()).orElse(null))
                .canEdit(canEdit)
                .canCancel(canCancel)
                .canReinspect(canReinspect)
                .build();
    }

    // ── 공통 ─────────────────────────────────────────────

    // 항목: 7개 중에서만, 값은 true/false (null 불가)
    private static Map<String, Boolean> validItems(Map<String, Boolean> items) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (items == null) return result;
        for (Map.Entry<String, Boolean> e : items.entrySet()) {
            if (!ContainerInspection.ITEM_KEYS.contains(e.getKey())) {
                throw new IllegalArgumentException("알 수 없는 점검 항목입니다: " + e.getKey());
            }
            if (e.getValue() == null) {
                throw new IllegalArgumentException("점검 항목 값은 양호(true) · 불량(false) 중 하나입니다: " + e.getKey());
            }
        }
        for (String key : ContainerInspection.ITEM_KEYS) {
            if (items.containsKey(key)) result.put(key, items.get(key));
        }
        return result;
    }

    private ContainerInspection find(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "점검 기록이 없습니다: " + id));
    }

    private ContainerInspection lock(Long id) {
        return inspectionRepository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "점검 기록이 없습니다: " + id));
    }

    // 중복 DRAFT(uk_ci_container_draft) · 동시 재점검(uk_ci_previous_active) 등 DB 제약 위반 → 409
    private ContainerInspection save(ContainerInspection c) {
        try {
            return inspectionRepository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException(conflictMessage(e));
        }
    }

    private static String conflictMessage(DataIntegrityViolationException e) {
        String m = String.valueOf(e.getMostSpecificCause().getMessage());
        if (m.contains("uk_ci_container_draft")) return "이 컨테이너에 작성 중인 점검 기록이 이미 있습니다.";
        if (m.contains("uk_ci_previous_active")) return "이미 이 기록의 재점검이 진행 중이거나 끝났습니다.";
        return "점검 기록을 저장할 수 없습니다 (DB 제약 위반).";
    }

    private void audit(ContainerInspection c, Action action, Account actor, Map<String, Object> before, String reason) {
        auditRepository.saveAndFlush(ContainerInspectionAudit.builder()
                .inspectionId(c.getInspectionId())
                .action(action)
                .actorAccountId(actor.getAccountId())
                .actorUserId(actor.getUserId())
                .beforeData(before)
                .afterData(snapshot(c))
                .reason(reason)
                .createdAt(OffsetDateTime.now())
                .build());
    }

    private static Map<String, Object> snapshot(ContainerInspection c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inspectionId", c.getInspectionId());
        m.put("orderId", c.getOrderId());
        m.put("containerNo", c.getContainerNo());
        m.put("companyId", c.getCompanyId());
        m.put("hubId", c.getHubId());
        m.put("driverAccountId", c.getDriverAccountId());
        m.put("items", new LinkedHashMap<>(c.getItems()));
        m.put("overallStatus", c.getOverallStatus());
        m.put("recordStatus", c.getRecordStatus());
        m.put("memo", c.getMemo());
        m.put("previousInspectionId", c.getPreviousInspectionId());
        m.put("finalizedAt", c.getFinalizedAt() == null ? null : c.getFinalizedAt().toString());
        m.put("cancelledAt", c.getCancelledAt() == null ? null : c.getCancelledAt().toString());
        m.put("cancelReason", c.getCancelReason());
        return m;
    }

    // 재점검 체인: 처음 기록까지 거슬러 올라간 뒤 다음 기록(취소 포함)을 모두 따라간다
    private List<ContainerInspection> chain(ContainerInspection c) {
        ContainerInspection root = c;
        Set<Long> seen = new HashSet<>();
        while (root.getPreviousInspectionId() != null && seen.add(root.getInspectionId())) {
            ContainerInspection prev = inspectionRepository.findById(root.getPreviousInspectionId()).orElse(null);
            if (prev == null) break;
            root = prev;
        }
        List<ContainerInspection> result = new ArrayList<>();
        Deque<ContainerInspection> queue = new ArrayDeque<>(List.of(root));
        Set<Long> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            ContainerInspection x = queue.poll();
            if (!visited.add(x.getInspectionId())) continue;
            result.add(x);
            queue.addAll(inspectionRepository.findByPreviousInspectionId(x.getInspectionId()));
        }
        return result;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
