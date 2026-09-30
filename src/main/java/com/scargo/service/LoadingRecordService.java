package com.scargo.service;

import com.scargo.dto.LoadingRecordCreateRequest;
import com.scargo.dto.LoadingRecordResponse;
import com.scargo.dto.LoadingRecordSearchCondition;
import com.scargo.dto.LoadingRecordUpdateRequest;
import com.scargo.entity.Container;
import com.scargo.entity.LoadingLocation;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.LoadingRecord.LoadingStatus;
import com.scargo.entity.Truck;
import com.scargo.repository.ContainerRepository;
import com.scargo.repository.LoadingLocationRepository;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoadingRecordService {

    private final LoadingRecordRepository loadingRecordRepository;
    private final TruckRepository truckRepository;
    private final ContainerRepository containerRepository;
    private final LoadingLocationRepository loadingLocationRepository;

    // 1. 적재 기록 생성 (+ 중복 검증, 위치 자동 동기화, Status 적용)
    @Transactional
    public LoadingRecordResponse createLoadingRecord(LoadingRecordCreateRequest request) {
        // 이미 완료되지 않은(진행 중인) 적재 작업 존재 여부 확인
        boolean isAlreadyInProgress = loadingRecordRepository
                .existsByContainer_ContainerNoAndStatusNot(request.getContainerNo(), LoadingStatus.COMPLETED);
        if (isAlreadyInProgress) {
            throw new IllegalStateException("해당 컨테이너(" + request.getContainerNo() + ")는 현재 진행 중인 적재 작업이 존재합니다.");
        }

        Truck truck = truckRepository.findById(request.getVehicleNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량 번호입니다: " + request.getVehicleNo()));

        Container container = containerRepository.findById(request.getContainerNo())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너 번호입니다: " + request.getContainerNo()));

        LoadingLocation location = loadingLocationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 장소 ID입니다: " + request.getLocationId()));

        // 컨테이너 실시간 적재 위치 동기화
        container.setLoadingLocation(location);

        LoadingRecord loadingRecord = LoadingRecord.builder()
                .truck(truck)
                .container(container)
                .location(location)
                .status(request.getStatusAsEnum()) // 요청받은 status 적용 (미입력 시 IN_PROGRESS)
                .build();

        LoadingRecord savedRecord = loadingRecordRepository.save(loadingRecord);
        return LoadingRecordResponse.from(savedRecord);
    }

    // 2. 적재 기록 단건 조회
    public LoadingRecordResponse getLoadingRecord(Long recordId) {
        LoadingRecord record = loadingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 기록 ID입니다: " + recordId));
        return LoadingRecordResponse.from(record);
    }

    // 3. 적재 기록 전체 조회 (페이징)
    public Page<LoadingRecordResponse> getAllLoadingRecords(Pageable pageable) {
        return loadingRecordRepository.findAll(pageable)
                .map(LoadingRecordResponse::from);
    }

    // 4. 특정 차량의 적재 기록 목록 조회 (페이징)
    public Page<LoadingRecordResponse> getLoadingRecordsByVehicleNo(String vehicleNo, Pageable pageable) {
        return loadingRecordRepository.findByTruck_VehicleNo(vehicleNo, pageable)
                .map(LoadingRecordResponse::from);
    }

    // 5. 특정 컨테이너의 적재 기록 목록 조회 (페이징)
    public Page<LoadingRecordResponse> getLoadingRecordsByContainerNo(String containerNo, Pageable pageable) {
        return loadingRecordRepository.findByContainer_ContainerNo(containerNo, pageable)
                .map(LoadingRecordResponse::from);
    }

    // 6. 특정 장소의 적재 기록 목록 조회 (페이징)
    public Page<LoadingRecordResponse> getLoadingRecordsByLocationId(Long locationId, Pageable pageable) {
        return loadingRecordRepository.findByLocation_LocationId(locationId, pageable)
                .map(LoadingRecordResponse::from);
    }

    // 7. 특정 차량의 최신 적재 기록 조회
    public LoadingRecordResponse getLatestLoadingRecordByVehicleNo(String vehicleNo) {
        LoadingRecord record = loadingRecordRepository.findFirstByTruck_VehicleNoOrderByRecordIdDesc(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("해당 차량의 적재 기록이 존재하지 않습니다: " + vehicleNo));
        return LoadingRecordResponse.from(record);
    }

    // 작업 상태(Status) 단독 변경
    @Transactional
    public LoadingRecordResponse updateStatus(Long recordId, LoadingStatus status) {
        LoadingRecord record = loadingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 기록 ID입니다: " + recordId));

        record.updateStatus(status);
        return LoadingRecordResponse.from(record);
    }

    // 기간(시작일~종료일)별 적재 기록 조회
    public Page<LoadingRecordResponse> getLoadingRecordsByPeriod(OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        return loadingRecordRepository.findByLoadedAtBetween(start, end, pageable)
                .map(LoadingRecordResponse::from);
    }

    // 다중 조건 검색 (QueryDSL / Custom Repository 연동)
    public Page<LoadingRecordResponse> searchLoadingRecords(LoadingRecordSearchCondition condition, Pageable pageable) {
        return loadingRecordRepository.searchLoadingRecords(condition, pageable)
                .map(LoadingRecordResponse::from);
    }

    // 8. 적재 기록 정보 수정 (Status 수정 항목 반영)
    @Transactional
    public LoadingRecordResponse updateLoadingRecord(Long recordId, LoadingRecordUpdateRequest request) {
        LoadingRecord record = loadingRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 기록 ID입니다: " + recordId));

        Truck newTruck = record.getTruck();
        if (request.getVehicleNo() != null) {
            newTruck = truckRepository.findById(request.getVehicleNo())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량 번호입니다: " + request.getVehicleNo()));
        }

        Container newContainer = record.getContainer();
        if (request.getContainerNo() != null) {
            newContainer = containerRepository.findById(request.getContainerNo())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컨테이너 번호입니다: " + request.getContainerNo()));
        }

        LoadingLocation newLocation = record.getLocation();
        if (request.getLocationId() != null) {
            newLocation = loadingLocationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 적재 장소 ID입니다: " + request.getLocationId()));
            
            // 변경된 장소 정보 컨테이너 엔티티에도 반영
            newContainer.setLoadingLocation(newLocation);
        }

        // status 전달 파라미터 반영
        record.update(newTruck, newContainer, newLocation, request.getStatusAsEnum());

        return LoadingRecordResponse.from(record);
    }

    // 9. 적재 기록 삭제
    @Transactional
    public void deleteLoadingRecord(Long recordId) {
        if (!loadingRecordRepository.existsById(recordId)) {
            throw new IllegalArgumentException("존재하지 않는 적재 기록 ID입니다: " + recordId);
        }
        loadingRecordRepository.deleteById(recordId);
    }
}