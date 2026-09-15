package com.scargo.service;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckResponse;
import com.scargo.entity.Company;
import com.scargo.entity.Truck;
import com.scargo.repository.CompanyRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckService {

    private final TruckRepository truckRepository;
    private final CompanyRepository companyRepository;

    // 차량 등록 로직
    @Transactional
    public TruckResponse createTruck(TruckCreateRequest request) {
        // 차량 번호판 중복 체크
        if (truckRepository.existsById(request.getVehicleNo())) {
            throw new IllegalArgumentException("이미 등록된 차량 번호입니다.");
        }

        // 소속 업체 존재 여부 확인
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + request.getCompanyId()));

        // Truck 엔티티 빌드 및 저장
        Truck truck = Truck.builder()
                .vehicleNo(request.getVehicleNo())
                .companyId(company.getCompanyId())
                .semiTrailer(request.isSemiTrailer())
                .trailerNo(request.getTrailerNo())
                .truckType(request.getTruckType())
                .maxLoadWeight(request.getMaxLoadWeight())
                .plannedRoute(request.getPlannedRoute())
                .build();

        Truck savedTruck = truckRepository.save(truck);
        return new TruckResponse(savedTruck);
    }

    // 전체 차량 목록 조회
    public List<TruckResponse> getAllTrucks() {
        return truckRepository.findAll().stream()
                .map(TruckResponse::new)
                .collect(Collectors.toList());
    }

    // 특정 차량 단건 조회
    public TruckResponse getTruck(String vehicleNo) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));
        return new TruckResponse(truck);
    }
    
    // 과적여부는 OverLoad테이블에 따로 
}