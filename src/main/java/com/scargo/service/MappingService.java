package com.scargo.service;

import com.scargo.dto.*;
import com.scargo.entity.Account;
import com.scargo.entity.Container;
import com.scargo.entity.Truck;
import com.scargo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingService {

    private final ContainerRepository containerRepository;
    private final TruckRepository truckRepository;
    private final AccountRepository accountRepository;

    public List<ContainerOptionResponse> getAvailableContainers(Long accountId) {
        Long companyId = getCompanyId(accountId);
        return containerRepository.findByCompanyIdAndAssignedVehicleNoIsNull(companyId).stream()
                .map(ContainerOptionResponse::new)
                .collect(Collectors.toList());
    }

    public List<TruckOptionResponse> getCompanyTrucks(Long accountId) {
        Long companyId = getCompanyId(accountId);
        return truckRepository.findByCompanyId(companyId).stream()
                .map(TruckOptionResponse::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void createMapping(Long accountId, MappingCreateRequest request) {
        Long companyId = getCompanyId(accountId);

        Container container = containerRepository.findById(request.getContainerNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));
        Truck truck = truckRepository.findById(request.getVehicleNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다."));

        if (!companyId.equals(container.getCompanyId())) {
            throw new IllegalArgumentException("소속 업체의 컨테이너가 아닙니다.");
        }
        if (!companyId.equals(truck.getCompanyId())) {
            throw new IllegalArgumentException("소속 업체의 차량이 아닙니다.");
        }
        if (container.getAssignedVehicleNo() != null) {
            throw new IllegalArgumentException("이미 다른 차량에 배정된 컨테이너입니다.");
        }
        containerRepository.findByAssignedVehicleNo(truck.getVehicleNo())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 다른 컨테이너를 배정받은 차량입니다.");
                });

        container.setAssignedVehicleNo(truck.getVehicleNo());
        container.setAssignedAt(OffsetDateTime.now());
    }

    @Transactional
    public void cancelMapping(Long accountId, String containerNo) {
        Long companyId = getCompanyId(accountId);

        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다."));

        if (!companyId.equals(container.getCompanyId())) {
            throw new IllegalArgumentException("소속 업체의 컨테이너가 아닙니다.");
        }

        container.setAssignedVehicleNo(null);
        container.setAssignedAt(null);
    }

    private Long getCompanyId(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));

        if (account.getCompanyId() == null) {
            throw new IllegalArgumentException("소속 업체가 없는 계정입니다.");
        }
        return account.getCompanyId();
    }
}