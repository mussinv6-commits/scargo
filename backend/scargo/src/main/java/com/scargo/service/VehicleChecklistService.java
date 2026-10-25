package com.scargo.service;

import com.scargo.dto.VehicleChecklistCreateRequest;
import com.scargo.dto.VehicleChecklistResponse;
import com.scargo.dto.VehicleChecklistUpdateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.VehicleChecklist;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.VehicleChecklistRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleChecklistService {
    private final VehicleChecklistRepository vehicleChecklistRepository;
    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    // 관리자 전체, 업체 자기 차량, 기사 본인 고정 차량, CONTROL 항만→허브 관제 차량
    private static final String SCOPE = """
(
    :role = 'ADMIN'
    OR (:role = 'CORPORATE_APPROVED' AND t.company_id = :companyId)
    OR (:role = 'CONTAINER_DRIVER' AND t.assigned_account_id = :accountId
        AND (t.vehicle_category = 'CONTAINER_TRUCK' OR t.vehicle_category IS NULL))
    OR (:role = 'LINEHAUL_DRIVER' AND t.assigned_account_id = :accountId
        AND (t.vehicle_category = 'GENERAL_TRUCK' OR t.vehicle_category IS NULL))
    OR (:role = 'CONTROL' AND EXISTS (
        SELECT 1 FROM shipment_legs l
        WHERE l.vehicle_no = t.vehicle_no
          AND l.leg_type = 'PORT_TO_HUB'
          AND l.status IN ('ASSIGNED', 'IN_TRANSIT', 'ARRIVED')
          AND l.assignment_id = (SELECT MAX(newer.assignment_id)
                                 FROM shipment_legs newer WHERE newer.vehicle_no = t.vehicle_no)
          AND NOT EXISTS (SELECT 1 FROM transport_orders o
                          WHERE o.assignment_id = l.assignment_id
                            AND o.status IN ('HANDED_OVER', 'CANCELED'))
    ))
)
            """;

    // 기존 boolean 인자는 호출 호환용이며 관리자 판정은 실제 DB 계정으로 수행
    public List<VehicleChecklistResponse> getAllChecklists(Authentication authentication, boolean isAdmin) {
        return list(currentAccount(authentication), null, null, null, null);
    }

    // 쓰기 권한·대상 차량·점검자 확인 후 점검표 생성
    @Transactional
    public VehicleChecklistResponse createChecklist(
            VehicleChecklistCreateRequest request, Authentication authentication, boolean isAdmin) {
        Account caller = currentAccount(authentication);
        requireWrite(caller);
        String vehicleNo = requireVehicleNo(request.getVehicleNo());
        requireVehicle(caller, vehicleNo);
        Long inspectorId = request.getInspectorAccountId() == null
                ? caller.getAccountId() : request.getInspectorAccountId();
        if (caller.getUserType() != Account.UserType.ADMIN && !caller.getAccountId().equals(inspectorId)) {
            throw new AccessDeniedException("다른 사용자를 점검자로 지정할 수 없습니다.");
        }
        accountRepository.findById(inspectorId).orElseThrow(() ->
                new IllegalArgumentException("존재하지 않는 점검자 계정입니다."));
        VehicleChecklist checklist = VehicleChecklist.builder()
                .vehicleNo(vehicleNo)
                .inspectorAccountId(inspectorId)
                .inspectionDate(request.getInspectionDate())
                .lightStatus(status(request.getLightStatus(), "PASS", false))
                .brakeStatus(status(request.getBrakeStatus(), "PASS", false))
                .airBrakeStatus(status(request.getAirBrakeStatus(), "PASS", false))
                .tireStatus(status(request.getTireStatus(), "PASS", false))
                .steeringStatus(status(request.getSteeringStatus(), "PASS", false))
                .cargoSecurementStatus(status(request.getCargoSecurementStatus(), "PASS", false))
                .engineOilStatus(status(request.getEngineOilStatus(), "PASS", false))
                .seatbeltStatus(status(request.getSeatbeltStatus(), "PASS", false))
                .fireExtinguisherStatus(status(request.getFireExtinguisherStatus(), "PASS", false))
                .safetyTriangleStatus(status(request.getSafetyTriangleStatus(), "PASS", false))
                .overallStatus(status(request.getOverallStatus(), "PASS", true))
                .memo(request.getMemo())
                .build();
        return VehicleChecklistResponse.from(vehicleChecklistRepository.saveAndFlush(checklist));
    }

    // 기록에 연결된 차량의 조회 범위를 확인한 뒤 단건 반환
    public VehicleChecklistResponse getChecklistById(
            Long inspectionId, Authentication authentication, boolean isAdmin) {
        return VehicleChecklistResponse.from(readableChecklist(inspectionId, currentAccount(authentication)));
    }

    // 본인 관리 범위 차량의 전체 점검 이력 조회
    public List<VehicleChecklistResponse> getChecklistsByVehicleNo(
            String vehicleNo, Authentication authentication, boolean isAdmin) {
        Account caller = currentAccount(authentication);
        String number = requireVehicleNo(vehicleNo);
        requireVehicle(caller, number);
        return list(caller, number, null, null, null);
    }

    // ADMIN은 모든 점검자, 나머지는 본인 작성 기록을 현재 차량 범위 안에서 조회
    public List<VehicleChecklistResponse> getChecklistsByInspector(Long inspectorAccountId) {
        Account caller = currentAccount(SecurityContextHolder.getContext().getAuthentication());
        if (inspectorAccountId == null) throw new IllegalArgumentException("점검자 ID가 필요합니다.");
        if (caller.getUserType() != Account.UserType.ADMIN
                && !caller.getAccountId().equals(inspectorAccountId)) {
            throw new AccessDeniedException("본인 점검 이력만 조회할 수 있습니다.");
        }
        return list(caller, null, inspectorAccountId, null, null);
    }

    // 지정 차량·일자의 점검표 조회
    public List<VehicleChecklistResponse> getChecklistsByVehicleAndDate(
            String vehicleNo, LocalDate inspectionDate, Authentication authentication, boolean isAdmin) {
        if (inspectionDate == null) throw new IllegalArgumentException("점검일이 필요합니다.");
        Account caller = currentAccount(authentication);
        String number = requireVehicleNo(vehicleNo);
        requireVehicle(caller, number);
        return list(caller, number, null, inspectionDate, inspectionDate);
    }

    // 시작일·종료일을 포함한 지정 차량의 기간별 점검 이력 조회
    public List<VehicleChecklistResponse> getChecklistsByPeriod(
            String vehicleNo, LocalDate startDate, LocalDate endDate,
            Authentication authentication, boolean isAdmin) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("조회 기간을 올바르게 입력해주세요.");
        }
        Account caller = currentAccount(authentication);
        String number = requireVehicleNo(vehicleNo);
        requireVehicle(caller, number);
        return list(caller, number, null, startDate, endDate);
    }

    // 쓰기 권한과 기록 범위를 확인하고 생략된 항목은 기존 값 유지
    @Transactional
    public VehicleChecklistResponse updateChecklist(
            Long inspectionId, VehicleChecklistUpdateRequest request,
            Authentication authentication, boolean isAdmin) {
        Account caller = currentAccount(authentication);
        requireWrite(caller);
        VehicleChecklist checklist = readableChecklist(inspectionId, caller);
        checklist.update(
                status(request.getLightStatus(), checklist.getLightStatus(), false),
                status(request.getBrakeStatus(), checklist.getBrakeStatus(), false),
                status(request.getAirBrakeStatus(), checklist.getAirBrakeStatus(), false),
                status(request.getTireStatus(), checklist.getTireStatus(), false),
                status(request.getSteeringStatus(), checklist.getSteeringStatus(), false),
                status(request.getCargoSecurementStatus(), checklist.getCargoSecurementStatus(), false),
                status(request.getEngineOilStatus(), checklist.getEngineOilStatus(), false),
                status(request.getSeatbeltStatus(), checklist.getSeatbeltStatus(), false),
                status(request.getFireExtinguisherStatus(), checklist.getFireExtinguisherStatus(), false),
                status(request.getSafetyTriangleStatus(), checklist.getSafetyTriangleStatus(), false),
                status(request.getOverallStatus(), checklist.getOverallStatus(), true),
                request.getMemo() == null ? checklist.getMemo() : request.getMemo()
        );
        vehicleChecklistRepository.flush();
        return VehicleChecklistResponse.from(checklist);
    }

    // 실제 ADMIN만 점검표 삭제
    @Transactional
    public void deleteChecklist(Long inspectionId, Authentication authentication, boolean isAdmin) {
        Account caller = currentAccount(authentication);
        if (caller.getUserType() != Account.UserType.ADMIN) {
            throw new AccessDeniedException("관리자만 점검표를 삭제할 수 있습니다.");
        }
        vehicleChecklistRepository.delete(readableChecklist(inspectionId, caller));
        vehicleChecklistRepository.flush();
    }

    // 클라이언트의 isAdmin 값이 아닌 로그인 계정의 실제 역할 확인
    private Account currentAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        Account account = accountRepository.findById(
                ((AuthenticatedAccountPrincipal) authentication.getPrincipal()).getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
        if (account.getUserType() == null || !Set.of(Account.UserType.ADMIN, Account.UserType.CONTROL,
                Account.UserType.CORPORATE_APPROVED, Account.UserType.CONTAINER_DRIVER,
                Account.UserType.LINEHAUL_DRIVER).contains(account.getUserType())) {
            throw new AccessDeniedException("차량 점검표 접근 권한이 없습니다.");
        }
        if (account.getUserType() == Account.UserType.CORPORATE_APPROVED && account.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 필요합니다.");
        }
        return account;
    }

    // 기사는 조회만 허용하고 기존 관리자·관제사·업체 담당자의 쓰기 권한 유지
    private void requireWrite(Account caller) {
        if (caller.getUserType() == Account.UserType.CONTAINER_DRIVER
                || caller.getUserType() == Account.UserType.LINEHAUL_DRIVER) {
            throw new AccessDeniedException("기사는 점검표 조회만 가능합니다.");
        }
    }

    // 차량 접근 범위를 SQL에서 확인
    private void requireVehicle(Account caller, String vehicleNo) {
        Map<String, Object> values = parameters(caller);
        values.put("vehicle", vehicleNo);
        Number count = (Number) bind(entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM trucks t WHERE " + SCOPE + " AND t.vehicle_no = :vehicle"), values)
                .getSingleResult();
        if (count.longValue() == 0) throw new AccessDeniedException("조회·관리할 수 없는 차량입니다.");
    }

    // 다른 업체 점검표의 존재 여부를 노출하지 않고 접근 가능한 기록만 조회
    private VehicleChecklist readableChecklist(Long id, Account caller) {
        if (id == null) throw new IllegalArgumentException("점검표 ID가 필요합니다.");
        Map<String, Object> values = parameters(caller);
        values.put("id", id);
        Number count = (Number) bind(entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM vehicle_checklist vc JOIN trucks t ON t.vehicle_no = vc.vehicle_no "
                        + "WHERE " + SCOPE + " AND vc.inspection_id = :id"), values).getSingleResult();
        if (count.longValue() == 0) throw new AccessDeniedException("존재하지 않거나 접근할 수 없는 점검표입니다.");
        return vehicleChecklistRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("점검표가 없습니다."));
    }

    // 모든 목록에 동일한 권한 SQL을 적용하고 점검일·ID 내림차순 정렬
    @SuppressWarnings("unchecked")
    private List<VehicleChecklistResponse> list(Account caller, String vehicleNo, Long inspectorId,
                                               LocalDate start, LocalDate end) {
        StringBuilder sql = new StringBuilder(
                "SELECT vc.* FROM vehicle_checklist vc JOIN trucks t ON t.vehicle_no = vc.vehicle_no WHERE ")
                .append(SCOPE);
        Map<String, Object> values = parameters(caller);
        if (vehicleNo != null) { sql.append(" AND vc.vehicle_no = :vehicle"); values.put("vehicle", vehicleNo); }
        if (inspectorId != null) { sql.append(" AND vc.inspector_account_id = :inspector"); values.put("inspector", inspectorId); }
        if (start != null) { sql.append(" AND vc.inspection_date >= :start"); values.put("start", start); }
        if (end != null) { sql.append(" AND vc.inspection_date <= :end"); values.put("end", end); }
        sql.append(" ORDER BY vc.inspection_date DESC, vc.inspection_id DESC");
        List<VehicleChecklist> records = bind(entityManager.createNativeQuery(sql.toString(), VehicleChecklist.class),
                values).getResultList();
        return records.stream().map(VehicleChecklistResponse::from).collect(Collectors.toList());
    }

    // 역할·계정·업체 SQL 파라미터 구성
    private Map<String, Object> parameters(Account caller) {
        Map<String, Object> values = new HashMap<>();
        values.put("role", caller.getUserType().name());
        values.put("accountId", caller.getAccountId());
        values.put("companyId", caller.getCompanyId() == null ? -1L : caller.getCompanyId());
        return values;
    }

    // 이름 있는 SQL 파라미터에 값 바인딩
    private Query bind(Query query, Map<String, Object> values) {
        values.forEach(query::setParameter);
        return query;
    }

    // 차량 번호 필수 입력 검사
    private String requireVehicleNo(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("차량 번호가 필요합니다.");
        return value.trim();
    }

    // 누락된 점검 항목은 기본·기존 값 유지, 개별 항목은 PASS/FAIL/NA, 종합 판정은 PASS/FAIL
    private String status(String value, String fallback, boolean overall) {
        if (value == null) return fallback;
        Set<String> allowed = overall ? Set.of("PASS", "FAIL") : Set.of("PASS", "FAIL", "NA");
        if (!allowed.contains(value)) throw new IllegalArgumentException("올바르지 않은 점검 상태입니다: " + value);
        return value;
    }
}
