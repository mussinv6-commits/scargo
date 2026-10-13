package com.scargo.service;

import com.scargo.dto.LoadingLocationCreateRequest;
import com.scargo.dto.LoadingLocationOptionResponse;
import com.scargo.dto.LoadingLocationResponse;
import com.scargo.dto.LoadingLocationUpdateRequest;
import com.scargo.entity.LoadingLocation;
import com.scargo.repository.LoadingLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoadingLocationService {

    private final LoadingLocationRepository loadingLocationRepository;

    // 로딩 장소(섹터) 생성 (중복명 방지 포함)
    @Transactional
    public LoadingLocationResponse createLoadingLocation(LoadingLocationCreateRequest request) {
        // 동일 야드 내 섹터명 중복 검증
        if (loadingLocationRepository.existsByYardIdAndSector(request.getYardId(), request.getSector())) {
            throw new IllegalArgumentException("이미 해당 야드에 존재하는 섹터명입니다: " + request.getSector());
        }

        // isAvailable은 status에 맞춰 엔티티 내부에서 자동 처리되므로 제외
        LoadingLocation location = LoadingLocation.builder()
                .yardId(request.getYardId())
                .sector(request.getSector())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(request.getStatus() != null ? request.getStatus() : "AVAILABLE")
                .build();

        LoadingLocation savedLocation = loadingLocationRepository.save(location);
        return new LoadingLocationResponse(savedLocation);
    }

    // 전체 로딩 장소 조회
    public List<LoadingLocationResponse> getAllLoadingLocations() {
        return loadingLocationRepository.findAll().stream()
                .map(LoadingLocationResponse::new)
                .collect(Collectors.toList());
    }

    // 특정 야드 ID에 속한 로딩 장소 목록 조회
    public List<LoadingLocationResponse> getLoadingLocationsByYard(Long yardId) {
        return loadingLocationRepository.findByYardId(yardId).stream()
                .map(LoadingLocationResponse::new)
                .collect(Collectors.toList());
    }

    // 드롭다운/셀렉트박스용 경량 옵션 목록 조회 (특정 야드의 사용 가능한 장소만 조회)
    public List<LoadingLocationOptionResponse> getLoadingLocationOptionsByYard(Long yardId) {
        return loadingLocationRepository.findByYardIdAndIsAvailable(yardId, true).stream()
                .map(LoadingLocationOptionResponse::from)
                .collect(Collectors.toList());
    }

    // 특정 야드 ID와 가용 여부(isAvailable)에 따른 로딩 장소 필터링 조회
    public List<LoadingLocationResponse> getLoadingLocationsByYardAndAvailability(Long yardId, Boolean isAvailable) {
        return loadingLocationRepository.findByYardIdAndIsAvailable(yardId, isAvailable).stream()
                .map(LoadingLocationResponse::new)
                .collect(Collectors.toList());
    }

    // 단건 로딩 장소 조회
    public LoadingLocationResponse getLoadingLocation(Long locationId) {
        LoadingLocation location = loadingLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장소입니다. ID: " + locationId));
        return new LoadingLocationResponse(location);
    }

    // 로딩 장소(섹터) 수정 (수정 시 섹터명 변경이 있다면 중복 검증 수행)
    @Transactional
    public LoadingLocationResponse updateLoadingLocation(Long locationId, LoadingLocationUpdateRequest request) {
        LoadingLocation location = loadingLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장소입니다. ID: " + locationId));

        if (request.getSector() != null && !location.getSector().equals(request.getSector())) {
            if (loadingLocationRepository.existsByYardIdAndSector(location.getYardId(), request.getSector())) {
                throw new IllegalArgumentException("이미 해당 야드에 존재하는 섹터명입니다: " + request.getSector());
            }
        }

        // 엔티티에 정의된 update 메서드 호출 (status가 바뀌면 is_available도 함께 동기화됨)
        location.update(
                location.getYardId(),
                request.getSector(),
                request.getLatitude(),
                request.getLongitude(),
                request.getStatus()
        );

        return new LoadingLocationResponse(location);
    }

    // 상태 전용 빠른 변경 메서드 (isAvailable 파라미터는 제거하고 status 기반으로 처리)
    @Transactional
    public LoadingLocationResponse updateLocationStatus(Long locationId, String status) {
        LoadingLocation location = loadingLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로딩 장소입니다. ID: " + locationId));

        // status만 넘겨서 업데이트 (내부적으로 is_available도 자동 세팅)
        location.update(
                location.getYardId(),
                location.getSector(),
                location.getLatitude(),
                location.getLongitude(),
                status
        );

        return new LoadingLocationResponse(location);
    }

    // 로딩 장소(섹터) 삭제
    @Transactional
    public void deleteLoadingLocation(Long locationId) {
        LoadingLocation location = loadingLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로딩 장소입니다. ID: " + locationId));
        
        loadingLocationRepository.delete(location);
    }
}