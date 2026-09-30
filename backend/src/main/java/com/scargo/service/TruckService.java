package com.scargo.service;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.dto.TruckResponse;
import com.scargo.dto.TruckUpdateRequest;
import com.scargo.entity.Company;
import com.scargo.entity.Truck;
import com.scargo.repository.CompanyRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckService {

    private final TruckRepository truckRepository;
    private final CompanyRepository companyRepository;

    // 차량 등록
    @Transactional
    public TruckResponse createTruck(TruckCreateRequest request) {
        // 차량 번호판 중복 체크
        if (truckRepository.existsById(request.getVehicleNo())) {
            throw new IllegalArgumentException("이미 등록된 차량 번호입니다. 번호: " + request.getVehicleNo());
        }

        // 소속 업체 존재 여부 확인
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + request.getCompanyId()));

        // Truck 엔티티 빌드
        Truck truck = Truck.builder()
                .vehicleNo(request.getVehicleNo())
                .company(company)
                .semiTrailer(request.isSemiTrailer()) 
                .trailerNo(request.getTrailerNo())
                .truckType(request.getTruckType())
                .maxLoadWeight(request.getMaxLoadWeight())
                .plannedRoute(request.getPlannedRoute())
                .build();

        Truck savedTruck = truckRepository.save(truck);
        return TruckResponse.from(savedTruck);
    }

    // 전체 차량 목록 조회
    public List<TruckResponse> getAllTrucks() {
        return truckRepository.findAll().stream()
                .map(TruckResponse::from)
                .toList();
    }

    // 특정 차량 단건 조회
    public TruckResponse getTruck(String vehicleNo) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));
        return TruckResponse.from(truck);
    }

    // 드롭다운/선택용 옵션 목록 조회 (최소 옵션만)
    public List<TruckOptionResponse> getTruckOptions(Long companyId) {
        return truckRepository.findByCompany_CompanyId(companyId).stream()
                .map(TruckOptionResponse::from)
                .toList();
    }

    // 차량 정보 수정
    @Transactional
    public TruckResponse updateTruck(String vehicleNo, TruckUpdateRequest request) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        Company company = null;
        if (request.getCompanyId() != null) {
            company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + request.getCompanyId()));
        }

        // 엔티티 비즈니스 메서드를 이용한 도메인 갱신
        truck.update(
                company,
                request.getSemiTrailer(),
                request.getTrailerNo(),
                request.getTruckType(),
                request.getMaxLoadWeight(),
                request.getPlannedRoute(),
                request.getStatus()
        );

        return TruckResponse.from(truck);
    }

    // 차량 상태만 개별 변경
    @Transactional
    public TruckResponse updateTruckStatus(String vehicleNo, String status) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        truck.updateStatus(status);
        return TruckResponse.from(truck);
    }

    // 차량 삭제
    @Transactional
    public void deleteTruck(String vehicleNo) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        truckRepository.delete(truck);
    }
}