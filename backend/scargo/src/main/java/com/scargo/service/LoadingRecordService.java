package com.scargo.service;

import com.scargo.dto.*;
import com.scargo.entity.Account;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingLocation;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.LoadingRecord.LoadingStatus;
import com.scargo.entity.Truck;
import com.scargo.repository.*;
import com.scargo.security.AuthenticatedAccountPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoadingRecordService {
    private final LoadingRecordRepository loadingRecordRepository;
    private final TruckRepository truckRepository;
    private final ContainerRepository containerRepository;
    private final LoadingLocationRepository loadingLocationRepository;
    private final AccountRepository accountRepository;
    private final EntityManager entityManager;

    // 역할별 공통 조회 범위: 관리자·관제사 전체, 운송사 자기 업체, 기사 본인 배차
    private static final String SCOPE = """
(
    :role IN ('ADMIN', 'CONTROL')
    OR (:role = 'CORPORATE_APPROVED'
        AND t.company_id = :companyId AND c.company_id = :companyId)
    OR (:role = 'CONTAINER_DRIVER' AND (
        EXISTS (SELECT 1 FROM transport_orders o
                WHERE o.driver_account_id = :accountId
                  AND o.shipment_id = lr.shipment_id
                  AND o.container_no = lr.container_no)
        OR (lr.shipment_id IS NULL
            AND t.assigned_account_id = :accountId
            AND lr.status IN ('PENDING', 'IN_PROGRESS')
            AND NOT EXISTS (SELECT 1 FROM loading_records newer
                            WHERE newer.vehicle_no = lr.vehicle_no
                              AND newer.record_id > lr.record_id))
    ))
)
            """;

    // 적재기록·차량·컨테이너 공통 JOIN
    private static final String FROM = """
             FROM loading_records lr
             JOIN trucks t ON t.vehicle_no = lr.vehicle_no
             JOIN containers c ON c.container_no = lr.container_no
             WHERE
            """;

    // 권한 범위 내 전체 적재기록을 페이징 조회
    public Page<LoadingRecordResponse> getAllLoadingRecords(Pageable pageable) {
        return search(new LoadingRecordSearchCondition(), pageable);
    }

    // 차량 번호가 일치하는 적재기록 조회
    public Page<LoadingRecordResponse> getLoadingRecordsByVehicleNo(String vehicleNo, Pageable pageable) {
        LoadingRecordSearchCondition filter = new LoadingRecordSearchCondition();
        filter.setVehicleNo(requireText(vehicleNo, "차량 번호"));
        return search(filter, pageable, true);
    }

    // 컨테이너 번호가 일치하는 적재기록 조회
    public Page<LoadingRecordResponse> getLoadingRecordsByContainerNo(String containerNo, Pageable pageable) {
        LoadingRecordSearchCondition filter = new LoadingRecordSearchCondition();
        filter.setContainerNo(requireText(containerNo, "컨테이너 번호"));
        return search(filter, pageable, true);
    }

    // 적재 장소별 기록 조회
    public Page<LoadingRecordResponse> getLoadingRecordsByLocationId(Long locationId, Pageable pageable) {
        if (locationId == null) throw new IllegalArgumentException("적재 장소 ID가 필요합니다.");
        LoadingRecordSearchCondition filter = new LoadingRecordSearchCondition();
        filter.setLocationId(locationId);
        return search(filter, pageable);
    }

    // 지정 기간의 적재기록 조회
    public Page<LoadingRecordResponse> getLoadingRecordsByPeriod(
            OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        if (start == null || end == null) throw new IllegalArgumentException("시작일과 종료일이 필요합니다.");
        LoadingRecordSearchCondition filter = new LoadingRecordSearchCondition();
        filter.setStartDate(start);
        filter.setEndDate(end);
        return search(filter, pageable);
    }

    // 여러 조건으로 권한 범위 내 적재기록 검색
    public Page<LoadingRecordResponse> searchLoadingRecords(
            LoadingRecordSearchCondition condition, Pageable pageable) {
        return search(condition == null ? new LoadingRecordSearchCondition() : condition, pageable);
    }

    // 조회 권한을 검사한 뒤 단건 기록 반환
    public LoadingRecordResponse getLoadingRecord(Long recordId) {
        Account caller = currentAccount();
        return LoadingRecordResponse.from(readableRecord(recordId, caller));
    }

    // 조회 가능한 차량 기록 중 ID가 가장 큰 기록 반환
    public LoadingRecordResponse getLatestLoadingRecordByVehicleNo(String vehicleNo) {
        Page<LoadingRecordResponse> page = getLoadingRecordsByVehicleNo(vehicleNo,
                org.springframework.data.domain.PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "recordId")));
        return page.getContent().stream().findFirst().orElseThrow(() ->
                new IllegalArgumentException("조회 가능한 적재 기록이 없습니다."));
    }

    // 권한·소속·중복 검사 후 적재기록 생성 및 컨테이너 위치 동기화
    @Transactional
    public LoadingRecordResponse createLoadingRecord(LoadingRecordCreateRequest request) {
        Account caller = currentAccount();
        requireWrite(caller);
        Truck truck = findTruck(request.getVehicleNo());
        Container container = lockContainer(request.getContainerNo());
        requireResources(caller, truck, container);
        LoadingLocation location = findLocation(request.getLocationId());
        LoadingStatus status = request.getStatusAsEnum();
        if (status == null) status = LoadingStatus.PENDING;
        checkActiveDuplicate(container.getContainerNo(), null, status);

        container.setLoadingLocation(location);
        LoadingRecord record = LoadingRecord.builder().truck(truck).container(container)
                .location(location).status(status).build();
        loadingRecordRepository.saveAndFlush(record);
        return LoadingRecordResponse.from(record);
    }

    // 쓰기 권한·중복 검사 후 작업 상태 변경
    @Transactional
    public LoadingRecordResponse updateStatus(Long recordId, LoadingStatus status) {
        if (status == null) throw new IllegalArgumentException("작업 상태가 필요합니다.");
        Account caller = currentAccount();
        requireWrite(caller);
        LoadingRecord record = readableRecord(recordId, caller);
        requireResources(caller, record.getTruck(), record.getContainer());
        lockContainer(record.getContainer().getContainerNo());
        checkActiveDuplicate(record.getContainer().getContainerNo(), recordId, status);
        record.updateStatus(status);
        loadingRecordRepository.flush();
        return LoadingRecordResponse.from(record);
    }

    // 기존·변경 자원의 소속 검사 후 적재기록 수정
    @Transactional
    public LoadingRecordResponse updateLoadingRecord(Long recordId, LoadingRecordUpdateRequest request) {
        Account caller = currentAccount();
        requireWrite(caller);
        LoadingRecord record = readableRecord(recordId, caller);
        requireResources(caller, record.getTruck(), record.getContainer());
        Truck truck = request.getVehicleNo() == null ? record.getTruck() : findTruck(request.getVehicleNo());
        Container container = lockContainer(request.getContainerNo() == null
                ? record.getContainer().getContainerNo() : request.getContainerNo());
        requireResources(caller, truck, container);
        LoadingLocation location = request.getLocationId() == null
                ? record.getLocation() : findLocation(request.getLocationId());
        LoadingStatus status = request.getStatusAsEnum() == null
                ? record.getStatus() : request.getStatusAsEnum();

        if ((!Objects.equals(truck.getVehicleNo(), record.getTruck().getVehicleNo())
                || !Objects.equals(container.getContainerNo(), record.getContainer().getContainerNo()))
                && count("SELECT COUNT(*) FROM loading_records WHERE record_id = :id AND shipment_id IS NOT NULL",
                         Map.of("id", recordId)) > 0) {
            throw new IllegalStateException("운송 건에 연결된 차량·컨테이너는 운송 재배정 절차에서 변경해주세요.");
        }
        checkActiveDuplicate(container.getContainerNo(), recordId, status);
        container.setLoadingLocation(location);
        record.update(truck, container, location, status);
        loadingRecordRepository.flush();
        return LoadingRecordResponse.from(record);
    }

    // 관리자만 적재기록 삭제
    @Transactional
    public void deleteLoadingRecord(Long recordId) {
        Account caller = currentAccount();
        if (caller.getUserType() != Account.UserType.ADMIN) {
            throw new AccessDeniedException("관리자만 적재 기록을 삭제할 수 있습니다.");
        }
        LoadingRecord record = readableRecord(recordId, caller);
        loadingRecordRepository.delete(record);
        loadingRecordRepository.flush();
    }

    // 부분 일치를 기본값으로 공통 검색에 위임
    private Page<LoadingRecordResponse> search(LoadingRecordSearchCondition filter, Pageable pageable) {
        return search(filter, pageable, false);
    }

    // 권한·검색 조건을 적용하여 DB에서 건수 계산·정렬·페이징
    @SuppressWarnings("unchecked")
    private Page<LoadingRecordResponse> search(LoadingRecordSearchCondition filter, Pageable pageable, boolean exact) {
        Account caller = currentAccount();
        if (pageable == null) throw new IllegalArgumentException("페이징 정보가 필요합니다.");
        if (filter.getStartDate() != null && filter.getEndDate() != null
                && filter.getStartDate().isAfter(filter.getEndDate())) {
            throw new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        Map<String, Object> parameters = scopeParameters(caller);
        StringBuilder where = new StringBuilder(FROM).append(SCOPE);
        addText(where, parameters, "lr.vehicle_no", "vehicle", filter.getVehicleNo(), exact);
        addText(where, parameters, "lr.container_no", "container", filter.getContainerNo(), exact);
        if (filter.getLocationId() != null) {
            where.append(" AND lr.location_id = :location"); parameters.put("location", filter.getLocationId());
        }
        if (filter.getStatus() != null) {
            where.append(" AND lr.status = :status"); parameters.put("status", filter.getStatus().name());
        }
        if (filter.getStartDate() != null) {
            where.append(" AND lr.loaded_at >= :start"); parameters.put("start", filter.getStartDate());
        }
        if (filter.getEndDate() != null) {
            where.append(" AND lr.loaded_at <= :end"); parameters.put("end", filter.getEndDate());
        }
        long total = count("SELECT COUNT(*)" + where, parameters);
        Query query = bind(entityManager.createNativeQuery("SELECT lr.*" + where
                + orderBy(pageable.getSort()), LoadingRecord.class), parameters);
        if (pageable.isPaged()) {
            if (pageable.getOffset() > Integer.MAX_VALUE) throw new IllegalArgumentException("조회 페이지가 너무 큽니다.");
            query.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
        }
        List<LoadingRecord> records = query.getResultList();
        return new PageImpl<>(records.stream().map(LoadingRecordResponse::from)
                .collect(Collectors.toList()), pageable, total);
    }

    // 대상 기록의 조회 가능 여부 검사 후 엔티티 반환
    private LoadingRecord readableRecord(Long recordId, Account caller) {
        if (recordId == null) throw new IllegalArgumentException("적재 기록 ID가 필요합니다.");
        Map<String, Object> parameters = scopeParameters(caller);
        parameters.put("id", recordId);
        if (count("SELECT COUNT(*)" + FROM + SCOPE + " AND lr.record_id = :id", parameters) == 0) {

            throw new AccessDeniedException("존재하지 않거나 조회 권한이 없는 적재 기록입니다.");
        }
        return loadingRecordRepository.findById(recordId).orElseThrow(() ->
                new IllegalArgumentException("적재 기록이 없습니다."));
    }

    // 로그인 계정을 DB에서 조회하고 허용 역할 검사
    private Account currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        Account account = accountRepository.findById(
                ((AuthenticatedAccountPrincipal) auth.getPrincipal()).getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
        if (account.getUserType() == null || !Set.of(Account.UserType.ADMIN, Account.UserType.CONTROL,
                Account.UserType.CONTAINER_DRIVER, Account.UserType.CORPORATE_APPROVED).contains(account.getUserType())) {
            throw new AccessDeniedException("적재 기록 조회 권한이 없습니다.");
        }
        if (account.getUserType() == Account.UserType.CORPORATE_APPROVED && account.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 필요합니다.");
        }
        return account;
    }

    // 권한 SQL에 사용할 역할·계정·업체 파라미터 구성
    private Map<String, Object> scopeParameters(Account caller) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("role", caller.getUserType().name());
        parameters.put("accountId", caller.getAccountId());

        parameters.put("companyId", caller.getCompanyId() == null ? -1L : caller.getCompanyId());
        return parameters;
    }

    // 컨테이너 기사의 쓰기 작업 차단
    private void requireWrite(Account caller) {
        if (caller.getUserType() == Account.UserType.CONTAINER_DRIVER) {
            throw new AccessDeniedException("기사는 적재 기록 조회만 가능합니다.");
        }
    }

    // 운송사 담당자의 차량·컨테이너 업체 소속 검사
    private void requireResources(Account caller, Truck truck, Container container) {
        if (caller.getUserType() == Account.UserType.CORPORATE_APPROVED
                && (!Objects.equals(caller.getCompanyId(), truck.getCompanyId())
                    || !Objects.equals(caller.getCompanyId(), container.getCompanyId()))) {
            throw new AccessDeniedException("자기 업체 차량·컨테이너만 지정할 수 있습니다.");
        }
    }

    // 동일 컨테이너의 진행 작업 중복 검사
    private void checkActiveDuplicate(String containerNo, Long recordId, LoadingStatus status) {
        if (status != LoadingStatus.PENDING && status != LoadingStatus.IN_PROGRESS) return;
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("container", containerNo);
        String sql = "SELECT COUNT(*) FROM loading_records WHERE container_no = :container "
                + "AND status IN ('PENDING', 'IN_PROGRESS')";
        if (recordId != null) { sql += " AND record_id <> :id"; parameters.put("id", recordId); }
        if (count(sql, parameters) > 0) throw new IllegalStateException("진행 중인 적재 작업이 이미 있습니다.");
    }

    // 차량 번호 검증 및 차량 조회
    private Truck findTruck(String vehicleNo) {
        return truckRepository.findById(requireText(vehicleNo, "차량 번호"))
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다."));
    }

    // 컨테이너 조회 및 비관적 쓰기 잠금 획득
    private Container lockContainer(String containerNo) {
        String id = requireText(containerNo, "컨테이너 번호");
        Container container = entityManager.find(Container.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (container == null) throw new IllegalArgumentException("존재하지 않는 컨테이너입니다.");
        return container;
    }

    // 적재 장소 ID 검증 및 존재 여부 확인
    private LoadingLocation findLocation(Long locationId) {
        if (locationId == null) throw new IllegalArgumentException("적재 장소 ID가 필요합니다.");
        return loadingLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 장소입니다."));
    }

    // 파라미터를 바인딩하여 SQL 건수 조회
    private long count(String sql, Map<String, Object> parameters) {
        return ((Number) bind(entityManager.createNativeQuery(sql), parameters).getSingleResult()).longValue();
    }

    // SQL의 이름 있는 파라미터에 값 바인딩
    private Query bind(Query query, Map<String, Object> parameters) {
        parameters.forEach(query::setParameter);
        return query;
    }

    // 필수 문자열 검사 및 양끝 공백 제거
    private String requireText(String text, String label) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException(label + "가 필요합니다.");
        return text.trim();
    }

    // 문자열 완전 일치 또는 부분 검색 조건 추가
    private void addText(StringBuilder sql, Map<String, Object> parameters,
                         String column, String name, String value, boolean exact) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append(exact ? " = :" : " LIKE :").append(name);

        parameters.put(name, exact ? value.trim() : "%" + value.trim() + "%");
    }

    // 허용된 정렬 필드만 SQL 정렬 조건으로 변환
    private String orderBy(Sort sort) {
        Map<String, String> columns = Map.of(
                "recordId", "lr.record_id", "loadedAt", "lr.loaded_at", "status", "lr.status",
                "truck.vehicleNo", "lr.vehicle_no", "container.containerNo", "lr.container_no",
                "location.locationId", "lr.location_id");
        List<String> order = new ArrayList<>();
        boolean recordIdPresent = false;
        for (Sort.Order item : sort) {
            String column = columns.get(item.getProperty());
            if (column == null) throw new IllegalArgumentException("지원하지 않는 정렬 항목입니다: " + item.getProperty());
            order.add(column + (item.isAscending() ? " ASC" : " DESC"));
            if (item.getProperty().equals("recordId")) recordIdPresent = true;
        }
        if (!recordIdPresent) order.add("lr.record_id DESC");
        return " ORDER BY " + String.join(", ", order);
    }
}
