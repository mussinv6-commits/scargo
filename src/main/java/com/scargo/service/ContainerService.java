package com.scargo.service;

import com.scargo.dto.ContainerCreateRequest;
import com.scargo.dto.ContainerResponse;
import com.scargo.dto.ContainerUpdateRequest;
import com.scargo.entity.Company;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingLocation;
import com.scargo.repository.CompanyRepository;
import com.scargo.repository.ContainerRepository;
import com.scargo.repository.LoadingLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContainerService {

    private final ContainerRepository containerRepository;
    private final CompanyRepository companyRepository;
    private final LoadingLocationRepository loadingLocationRepository;

    // 컨테이너 등록 로직
    @Transactional
    public ContainerResponse createContainer(ContainerCreateRequest request) {
        // 1.컨테이너 번호 중복 체크
        if (containerRepository.existsById(request.getContainerNo())) {
            throw new IllegalArgumentException("이미 등록된 컨테이너 번호입니다.");
        }

        // 2.소속 업체 존재 여부 확인
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + request.getCompanyId()));

        // 3.적재 장소 존재 여부 확인
        LoadingLocation loadingLocation = loadingLocationRepository.findById(request.getLoadingLocationId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 장소입니다. ID: " + request.getLoadingLocationId()));

        // 4. DB에 저장
        Container container = Container.builder()
                .containerNo(request.getContainerNo())
                .companyId(company.getCompanyId())
                .reservedCargoInfo(request.getReservedCargoInfo())
                .loadingLocation(loadingLocation)
                .containerType(request.getContainerType())
                .build();

        Container savedContainer = containerRepository.save(container);
        return new ContainerResponse(savedContainer);
    }

    // 전체 컨테이너 목록 조회
    public List<ContainerResponse> getAllContainers() {
        return containerRepository.findAll().stream()
                .map(ContainerResponse::new)
                .collect(Collectors.toList());
    }

    // 특정 컨테이너 단건 조회
    public ContainerResponse getContainer(String containerNo) {
        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. 번호: " + containerNo));
        return new ContainerResponse(container);
    }

    // 컨테이너 정보 수정 로직
    @Transactional
    public ContainerResponse updateContainer(String containerNo, ContainerUpdateRequest request) {
        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. 번호: " + containerNo));

        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + request.getCompanyId()));

        LoadingLocation loadingLocation = loadingLocationRepository.findById(request.getLoadingLocationId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 장소입니다. ID: " + request.getLoadingLocationId()));

        container.setCompanyId(company.getCompanyId());
        container.setReservedCargoInfo(request.getReservedCargoInfo());
        container.setLoadingLocation(loadingLocation);
        container.setContainerType(request.getContainerType());

        return new ContainerResponse(container);
    }
    
   // 컨테이너 삭제 로직
    @Transactional
    public void deleteContainer(String containerNo) {
        Container container = containerRepository.findById(containerNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너입니다. 번호: " + containerNo));
        
        containerRepository.delete(container);
    }
}