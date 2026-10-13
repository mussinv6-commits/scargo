package com.scargo.service;

import com.scargo.dto.GateCreateRequest;
import com.scargo.dto.GateResponse;
import com.scargo.dto.GateUpdateRequest;
import com.scargo.entity.Gate;
import com.scargo.repository.GateRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateService {

    private final GateRepository gateRepository;

    // 1. 게이트 등록
    @Transactional
    public Long createGate(GateCreateRequest request) {
        // 게이트 코드 중복 체크
        if (gateRepository.existsByGateCode(request.getGateCode())) {
            throw new IllegalArgumentException("이미 존재하는 게이트 코드입니다: " + request.getGateCode());
        }

        Gate gate = Gate.builder()
                .gateCode(request.getGateCode())
                .gateName(request.getGateName())
                .gateType(request.getGateType())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .locationDescription(request.getLocationDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Gate savedGate = gateRepository.save(gate);
        return savedGate.getGateId();
    }

    // 2. 전체 게이트 목록 조회
    public List<GateResponse> getAllGates() {
        return gateRepository.findAll().stream()
                .map(GateResponse::from)
                .collect(Collectors.toList());
    }

    // 2-1. 활성화된(사용 중인) 게이트 목록 조회
    public List<GateResponse> getActiveGates() {
        return gateRepository.findByIsActiveTrue().stream()
                .map(GateResponse::from)
                .collect(Collectors.toList());
    }

    // 2-2. 게이트 유형별(IN/OUT/BOTH) 목록 조회
    public List<GateResponse> getGatesByType(String gateType) {
        return gateRepository.findByGateType(gateType).stream()
                .map(GateResponse::from)
                .collect(Collectors.toList());
    }

    // 3. 특정 게이트 단건 조회
    public GateResponse getGateById(Long gateId) {
        Gate gate = findGateEntityById(gateId);
        return GateResponse.from(gate);
    }

    // 4. 게이트 정보 수정
    @Transactional
    public void updateGate(Long gateId, GateUpdateRequest request) {
        Gate gate = findGateEntityById(gateId);

        // 게이트 코드가 수정되는 경우 중복 검사
        if (request.getGateCode() != null && !gate.getGateCode().equals(request.getGateCode())) {
            if (gateRepository.existsByGateCode(request.getGateCode())) {
                throw new IllegalArgumentException("이미 존재하는 게이트 코드입니다: " + request.getGateCode());
            }
        }

        // 앞서 엔티티에 정의한 update 메서드 호출
        gate.update(request);
    }

    // 5. 게이트 소프트 삭제 (실제 삭제 대신 비활성화 처리)
    @Transactional
    public void deleteGate(Long gateId) {
        Gate gate = findGateEntityById(gateId);
        // 물리 삭제 대신 엔티티의 delete 메서드를 호출하여 isActive를 false로 변경
        gate.delete();
    }

    // [공통 내부 메서드] ID로 Gate 엔티티 조회 (없으면 예외 발생)
    private Gate findGateEntityById(Long gateId) {
        return gateRepository.findById(gateId)
                .orElseThrow(() -> new EntityNotFoundException("해당 게이트를 찾을 수 없습니다. ID: " + gateId));
    }
}