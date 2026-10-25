package com.scargo.service;

import com.scargo.dto.*;
import com.scargo.entity.Account;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.LoadingRecord.LoadingStatus;
import com.scargo.entity.Truck;
import com.scargo.repository.*;
import com.scargo.security.AuthenticatedAccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// 26.10.22 수정: 매핑 권한을 서비스 계층에서도 확인
//  ADMIN · CONTROL = 전체 업체, CORPORATE_APPROVED = 자기 업체만, 그 외 역할 403
//  역할과 관계없이 차량과 컨테이너는 같은 업체 소속이어야 하고, 소속 업체가 없는 차량 · 컨테이너는 배정 불가
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingService {

    // 진행 중인 적재 기록 상태 (DB uk_loading_active_container 기준: 컨테이너당 최대 1건)
    private static final Set<LoadingStatus> ACTIVE_LOADING = Set.of(LoadingStatus.PENDING, LoadingStatus.IN_PROGRESS);

    private final ContainerRepository containerRepository;
    private final TruckRepository truckRepository;
    private final AccountRepository accountRepository;
    private final LoadingRecordRepository loadingRecordRepository;
    private final DriverAlertService driverAlertService;

    // 1. 배정 가능한 컨테이너 목록 조회
    public List<ContainerOptionResponse> getAvailableContainers(Authentication authentication) {
        Long companyId = scopeCompanyId(authentication);
        List<Container> containers = companyId == null
                ? containerRepository.findByCompanyIdIsNotNullAndAssignedVehicleNoIsNull()
                : containerRepository.findByCompanyIdAndAssignedVehicleNoIsNull(companyId);
        return containers.stream()
                .map(ContainerOptionResponse::new)
                .toList();
    }

    // 2. 소속 업체의 전체 차량 목록 조회 (ADMIN · CONTROL 은 전체 업체)
    public List<TruckOptionResponse> getCompanyTrucks(Authentication authentication) {
        Long companyId = scopeCompanyId(authentication);
        List<Truck> trucks = companyId == null
                ? truckRepository.findAll()
                : truckRepository.findByCompany_CompanyId(companyId);
        return trucks.stream()
                .map(TruckOptionResponse::new)
                .toList();
    }

    // 3. 차량 - 컨테이너 매핑 생성
    @Transactional
    public void createMapping(Authentication authentication, MappingCreateRequest request) {
        Long companyId = scopeCompanyId(authentication);

        // 컨테이너 → 차량 순서로 잠가서 동시 요청의 중복 배정을 막는다
        Container container = lockContainer(request.getContainerNo());
        Truck truck = lockTruck(request.getVehicleNo());

        // 3-1. 소속 업체 보안 검증
        requirePair(companyId, container, truck);
        // 26.10.22 추가: DB 트리거 · NOT NULL 제약에 걸려 500 이 나기 전에 업무 규칙으로 거부 (400)
        requireSemiTrailer(truck);
        requireLoadingLocation(container);

        // 3-2. 이미 배정 여부 검증
        if (container.getAssignedVehicleNo() != null) {
            throw new IllegalArgumentException("이미 다른 차량에 배정된 컨테이너입니다.");
        }
        containerRepository.findByAssignedVehicleNo(truck.getVehicleNo())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 다른 컨테이너를 배정받은 차량입니다.");
                });
        if (activeRecord(container.getContainerNo()).isPresent()) {
            throw new IllegalStateException("진행 중인 적재 기록이 남아 있는 컨테이너입니다.");
        }

        // 3-3. 컨테이너 상태 변경 (도메인 메서드 또는 setter 활용)
        OffsetDateTime now = OffsetDateTime.now();
        container.setAssignedVehicleNo(truck.getVehicleNo());
        container.setAssignedAt(now);

        // 3-4. loading_records 이력 저장 (loadedAt은 DB/엔티티 매핑 설정에 따라 자동 바인딩되므로 제외)
        LoadingRecord loadingRecord = LoadingRecord.builder()
                .truck(truck)
                .container(container)
                .location(container.getLoadingLocation())
                .build();

        loadingRecordRepository.save(loadingRecord);
        driverAlertService.notifyContainerMapped(truck, container);
    }

    // 4. 매핑 해제/취소
    @Transactional
    public void cancelMapping(Authentication authentication, String containerNo) {
        Long companyId = scopeCompanyId(authentication);

        Container container = lockContainer(containerNo);
        requireOwnContainer(companyId, container);

        // 26.10.22 추가: 매핑으로 만든 작업 대기(PENDING) 기록은 취소 처리, 작업 중이면 해제 불가
        Optional<LoadingRecord> active = activeRecord(containerNo);
        requireNotInProgress(active);
        active.ifPresent(r -> r.updateStatus(LoadingStatus.CANCELED));

        container.setAssignedVehicleNo(null);
        container.setAssignedAt(null);
    }

    // 26.10.01 병합: 4-1. 매핑 수정 - 컨테이너에 배정된 차량을 다른 차량으로 변경
    // (프론트 사업자 매핑 화면의 "수정" 버튼: PUT /api/mappings/{containerNo})
    @Transactional
    public void changeMapping(Authentication authentication, String containerNo, MappingUpdateRequest request) {
        Long companyId = scopeCompanyId(authentication);

        Container container = lockContainer(containerNo);
        Truck newTruck = lockTruck(request.getVehicleNo());

        // 소속 업체 보안 검증
        requirePair(companyId, container, newTruck);
        requireSemiTrailer(newTruck);

        String oldVehicleNo = container.getAssignedVehicleNo();
        if (oldVehicleNo == null) {
            throw new IllegalArgumentException("차량이 배정되지 않은 컨테이너입니다. 먼저 매핑을 생성해주세요.");
        }
        if (oldVehicleNo.equals(newTruck.getVehicleNo())) {
            throw new IllegalArgumentException("이미 해당 차량에 배정된 컨테이너입니다.");
        }
        containerRepository.findByAssignedVehicleNo(newTruck.getVehicleNo())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 다른 컨테이너를 배정받은 차량입니다.");
                });

        Optional<LoadingRecord> active = activeRecord(containerNo);
        requireNotInProgress(active);
        if (active.isEmpty()) {
            // 새 적재 기록을 만들어야 하므로 적재 장소가 필요
            requireLoadingLocation(container);
        }

        // 컨테이너 배정 차량 변경
        container.setAssignedVehicleNo(newTruck.getVehicleNo());
        container.setAssignedAt(OffsetDateTime.now());

        // 아직 시작 전(PENDING)인 적재기록이 있으면 차량만 바꿔주고, 없으면 새로 만든다
        if (active.isPresent()) {
            active.get().update(newTruck, null, null, null);
        } else {
            loadingRecordRepository.save(LoadingRecord.builder()
                    .truck(newTruck)
                    .container(container)
                    .location(container.getLoadingLocation())
                    .build());
        }

        driverAlertService.notifyContainerMapped(newTruck, container);
    }

    // [추가] 5. 현재 매핑된 목록 조회 (소속 업체 기준, ADMIN · CONTROL 은 전체 업체)
    public List<ContainerResponse> getActiveMappings(Authentication authentication) {
        Long companyId = scopeCompanyId(authentication);
        List<Container> containers = companyId == null
                ? containerRepository.findByAssignedVehicleNoIsNotNull()
                : containerRepository.findByCompanyIdAndAssignedVehicleNoIsNotNull(companyId);
        return containers.stream()
                .map(ContainerResponse::new)
                .toList();
    }

    // Helper: 로그인 계정의 매핑 범위. null = 전체 업체(ADMIN · CONTROL), 값 = 그 업체만(CORPORATE_APPROVED)
    private Long scopeCompanyId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        Account account = accountRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));

        Account.UserType type = account.getUserType();
        if (type == Account.UserType.ADMIN || type == Account.UserType.CONTROL) {
            return null;
        }
        if (type == Account.UserType.CORPORATE_APPROVED) {
            if (account.getCompanyId() == null) {
                throw new AccessDeniedException("소속된 업체 정보가 없는 계정입니다.");
            }
            return account.getCompanyId();
        }
        throw new AccessDeniedException("차량-컨테이너 매핑 권한이 없습니다.");
    }

    // Helper: 운송사 담당자는 자기 업체 컨테이너만 (403)
    private void requireOwnContainer(Long companyId, Container container) {
        if (companyId != null && !companyId.equals(getContainerCompanyId(container))) {
            throw new AccessDeniedException("소속 업체의 컨테이너가 아닙니다.");
        }
    }

    // Helper: 운송사 담당자 범위(403) → 소속 업체 존재 · 같은 업체 여부(400). ADMIN · CONTROL 도 업체가 다르면 거부
    private void requirePair(Long companyId, Container container, Truck truck) {
        requireOwnContainer(companyId, container);
        Long truckCompanyId = getTruckCompanyId(truck);
        if (companyId != null && !companyId.equals(truckCompanyId)) {
            throw new AccessDeniedException("소속 업체의 차량이 아닙니다.");
        }

        Long containerCompanyId = getContainerCompanyId(container);
        if (containerCompanyId == null) {
            throw new IllegalArgumentException("소속 업체가 없는 컨테이너는 배정할 수 없습니다.");
        }
        if (truckCompanyId == null) {
            throw new IllegalArgumentException("소속 업체가 없는 차량에는 배정할 수 없습니다.");
        }
        if (!containerCompanyId.equals(truckCompanyId)) {
            throw new IllegalArgumentException("차량과 컨테이너의 소속 업체가 다릅니다.");
        }
    }

    // Helper: 상하차 작업 중(IN_PROGRESS)인 컨테이너는 매핑 변경 · 해제 불가 (409)
    private void requireNotInProgress(Optional<LoadingRecord> active) {
        if (active.isPresent() && active.get().getStatus() == LoadingStatus.IN_PROGRESS) {
            throw new IllegalStateException("상하차 작업이 진행 중인 컨테이너는 매핑을 변경하거나 해제할 수 없습니다.");
        }
    }

    // Helper: loading_records 트리거(scargo_validate_loading_truck)와 같은 규칙 - 세미트레일러만 컨테이너 적재 (400)
    private void requireSemiTrailer(Truck truck) {
        if (!truck.isSemiTrailer()) {
            throw new IllegalArgumentException("세미트레일러 차량에만 컨테이너를 배정할 수 있습니다. 차량: " + truck.getVehicleNo());
        }
    }

    // Helper: loading_records.location_id NOT NULL - 적재 장소가 없는 컨테이너는 적재 기록을 만들 수 없음 (400)
    private void requireLoadingLocation(Container container) {
        if (container.getLoadingLocation() == null) {
            throw new IllegalArgumentException("적재 장소가 지정되지 않은 컨테이너입니다. 컨테이너: " + container.getContainerNo());
        }
    }

    private Optional<LoadingRecord> activeRecord(String containerNo) {
        return loadingRecordRepository.findFirstByContainer_ContainerNoAndStatusInOrderByRecordIdDesc(
                containerNo, ACTIVE_LOADING);
    }

    private Container lockContainer(String containerNo) {
        return containerRepository.findForUpdate(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. ID: " + containerNo));
    }

    private Truck lockTruck(String vehicleNo) {
        return truckRepository.findForUpdate(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));
    }

    // Helper: Truck 엔티티에서 안전하게 CompanyId 추출
    private Long getTruckCompanyId(Truck truck) {
        if (truck == null || truck.getCompany() == null) {
            return null;
        }
        return truck.getCompany().getCompanyId();
    }

    // Helper: Container 엔티티에서 안전하게 CompanyId 추출
    private Long getContainerCompanyId(Container container) {
        if (container == null) {
            return null;
        }
        return container.getCompanyId();
    }
}
