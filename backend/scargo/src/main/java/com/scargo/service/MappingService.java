package com.scargo.service;

import com.scargo.dto.*;
import com.scargo.entity.Account;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.Truck;
import com.scargo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingService {

    private final ContainerRepository containerRepository;
    private final TruckRepository truckRepository;
    private final AccountRepository accountRepository;
    private final LoadingRecordRepository loadingRecordRepository;
    private final DriverAlertService driverAlertService;

    // 1. 배정 가능한 컨테이너 목록 조회
    public List<ContainerOptionResponse> getAvailableContainers(Long accountId) {
        Long companyId = getCompanyId(accountId);
        return containerRepository.findByCompanyIdAndAssignedVehicleNoIsNull(companyId).stream()
                .map(ContainerOptionResponse::new)
                .toList();
    }

    // 2. 소속 업체의 전체 차량 목록 조회
    public List<TruckOptionResponse> getCompanyTrucks(Long accountId) {
        Long companyId = getCompanyId(accountId);
        return truckRepository.findByCompany_CompanyId(companyId).stream()
                .map(TruckOptionResponse::new)
                .toList();
    }

    // 3. 차량 - 컨테이너 매핑 생성
    @Transactional
    public void createMapping(Long accountId, MappingCreateRequest request) {
        Long companyId = getCompanyId(accountId);

        Container container = containerRepository.findById(request.getContainerNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. ID: " + request.getContainerNo()));
        Truck truck = truckRepository.findById(request.getVehicleNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + request.getVehicleNo()));

        // 3-1. 소속 업체 보안 검증
        if (!companyId.equals(getTruckCompanyId(truck))) {
            throw new IllegalArgumentException("소속 업체의 차량이 아닙니다.");
        }
        if (!companyId.equals(getContainerCompanyId(container))) {
            throw new IllegalArgumentException("소속 업체의 컨테이너가 아닙니다.");
        }

        // 3-2. 이미 배정 여부 검증
        if (container.getAssignedVehicleNo() != null) {
            throw new IllegalArgumentException("이미 다른 차량에 배정된 컨테이너입니다.");
        }
        containerRepository.findByAssignedVehicleNo(truck.getVehicleNo())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 다른 컨테이너를 배정받은 차량입니다.");
                });

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
    public void cancelMapping(Long accountId, String containerNo) {
        Long companyId = getCompanyId(accountId);

        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. ID: " + containerNo));

        if (!companyId.equals(getContainerCompanyId(container))) {
            throw new IllegalArgumentException("소속 업체의 컨테이너가 아닙니다.");
        }

        container.setAssignedVehicleNo(null);
        container.setAssignedAt(null);
    }

    // 26.10.01 병합: 4-1. 매핑 수정 - 컨테이너에 배정된 차량을 다른 차량으로 변경
    // (프론트 사업자 매핑 화면의 "수정" 버튼: PUT /api/mappings/{containerNo})
    @Transactional
    public void changeMapping(Long accountId, String containerNo, MappingUpdateRequest request) {
        Long companyId = getCompanyId(accountId);

        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. ID: " + containerNo));
        Truck newTruck = truckRepository.findById(request.getVehicleNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + request.getVehicleNo()));

        // 소속 업체 보안 검증
        if (!companyId.equals(getContainerCompanyId(container))) {
            throw new IllegalArgumentException("소속 업체의 컨테이너가 아닙니다.");
        }
        if (!companyId.equals(getTruckCompanyId(newTruck))) {
            throw new IllegalArgumentException("소속 업체의 차량이 아닙니다.");
        }

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

        // 컨테이너 배정 차량 변경
        container.setAssignedVehicleNo(newTruck.getVehicleNo());
        container.setAssignedAt(OffsetDateTime.now());

        // 아직 시작 전(PENDING)인 적재기록이 있으면 차량만 바꿔주고, 없으면 새로 만든다
        LoadingRecord pending = loadingRecordRepository
                .findFirstByTruck_VehicleNoAndStatusOrderByRecordIdDesc(oldVehicleNo, LoadingRecord.LoadingStatus.PENDING)
                .filter(r -> r.getContainer() != null && containerNo.equals(r.getContainer().getContainerNo()))
                .orElse(null);
        if (pending != null) {
            pending.update(newTruck, null, null, null);
        } else {
            loadingRecordRepository.save(LoadingRecord.builder()
                    .truck(newTruck)
                    .container(container)
                    .location(container.getLoadingLocation())
                    .build());
        }

        driverAlertService.notifyContainerMapped(newTruck, container);
    }

    // [추가] 5. 현재 매핑된 목록 조회 (소속 업체 기준)
    public List<ContainerResponse> getActiveMappings(Long accountId) {
        Long companyId = getCompanyId(accountId);
        return containerRepository.findByCompanyIdAndAssignedVehicleNoIsNotNull(companyId).stream()
                .map(ContainerResponse::new)
                .toList();
    }

    // Helper: 계정의 소속 업체 ID 조회
    private Long getCompanyId(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        Long companyId = account.getCompanyId();
        if (companyId == null) {
            throw new IllegalArgumentException("소속된 업체 정보가 없는 계정입니다.");
        }
        return companyId;
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