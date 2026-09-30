package com.scargo.service;

import com.scargo.dto.YardCreateRequest;
import com.scargo.dto.YardOptionResponse;
import com.scargo.dto.YardResponse;
import com.scargo.dto.YardUpdateRequest;
import com.scargo.entity.Yard;
import com.scargo.repository.YardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class YardService {

    private final YardRepository yardRepository;

    // 야드 등록 로직
    @Transactional
    public YardResponse createYard(YardCreateRequest request) {
        // 야드 이름 중복 체크
        yardRepository.findByYardName(request.getYardName())
                .ifPresent(y -> {
                    throw new IllegalArgumentException("이미 존재하는 야드 이름입니다: " + request.getYardName());
                });

        Yard yard = Yard.builder()  // 야드 등록
                .yardName(request.getYardName())  // 야드명
                .yardType(request.getYardType())  // 야드 타입
                .latitude(request.getLatitude())    // 위도
                .longitude(request.getLongitude())  // 경도
                .status(request.getStatus())     // 야드 상태
                .isAvailable(request.getIsAvailable())  // 야드 사용 가능여부
                .build();  // 최종생성

        Yard savedYard = yardRepository.save(yard);
        return new YardResponse(savedYard);
    }

    // 전체 야드 목록 조회
    public List<YardResponse> getAllYards() {
        return yardRepository.findAll().stream()
                .map(YardResponse::new)
                .collect(Collectors.toList());
    }

    // 드롭다운/옵션용 경량 야드 목록 조회
    public List<YardOptionResponse> getYardOptions() {
        return yardRepository.findAll().stream()
                .map(YardOptionResponse::from)
                .collect(Collectors.toList());
    }

    // 단건 야드 조회 (yardId 기준)
    public YardResponse getYard(Long yardId) {
        Yard yard = yardRepository.findById(yardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 야드입니다. ID: " + yardId));
        return new YardResponse(yard);
    }

    // 야드 타입별 조회
    public List<YardResponse> getYardsByType(String yardType) {
        return yardRepository.findByYardType(yardType).stream()
                .map(YardResponse::new)
                .collect(Collectors.toList());
    }

    // 사용 가능 여부별 조회
    public List<YardResponse> getYardsByAvailability(Boolean isAvailable) {
        return yardRepository.findByIsAvailable(isAvailable).stream()
                .map(YardResponse::new)
                .collect(Collectors.toList());
    }

    // 야드 수정 로직
    @Transactional
    public YardResponse updateYard(Long yardId, YardUpdateRequest request) {
        Yard yard = yardRepository.findById(yardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 야드입니다. ID: " + yardId));

        // 야드 이름을 변경하는 경우 중복 체크
        if (request.getYardName() != null && !request.getYardName().equals(yard.getYardName())) {
            yardRepository.findByYardName(request.getYardName())
                    .ifPresent(y -> {
                        throw new IllegalArgumentException("이미 존재하는 야드 이름입니다: " + request.getYardName());
                    });
        }

        // 엔티티 업데이트 (위도, 경도 포함)
        yard.update(
                request.getYardName(), 
                request.getYardType(), 
                request.getStatus(), 
                request.getIsAvailable(), 
                request.getLatitude(), 
                request.getLongitude()
        );

        return new YardResponse(yard);
    }

    // 야드 삭제 로직
    @Transactional
    public void deleteYard(Long yardId) {
        Yard yard = yardRepository.findById(yardId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 야드입니다. ID: " + yardId));
        
        yardRepository.delete(yard);
    }
}