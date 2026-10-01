package com.scargo.service;

import com.scargo.dto.TireManagementCreateRequest;
import com.scargo.dto.TireManagementResponse;
import com.scargo.dto.TireManagementUpdateRequest;
import com.scargo.entity.TireManagement;
import com.scargo.repository.TireManagementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TireManagementService {

    private final TireManagementRepository tireManagementRepository;

    // 타이어 신규 장착 등록
    @Transactional
    public TireManagementResponse createTire(TireManagementCreateRequest request) {
        TireManagement tire = TireManagement.builder()
                .vehicleNo(request.getVehicleNo())
                .axlePosition(request.getAxlePosition())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVE")
                .installationDate(request.getInstallationDate() != null ? request.getInstallationDate() : OffsetDateTime.now())
                .installationMileage(request.getInstallationMileage())
                .memo(request.getMemo())
                .build();

        TireManagement savedTire = tireManagementRepository.save(tire);
        return TireManagementResponse.from(savedTire);
    }

    // 특정 차량의 현재 사용 중(ACTIVE)인 타이어 목록 조회
    public List<TireManagementResponse> getActiveTiresByVehicle(String vehicleNo) {
        List<TireManagement> tires = tireManagementRepository.findByVehicleNoAndStatus(vehicleNo, "ACTIVE");
        return tires.stream()
                .map(TireManagementResponse::from)
                .collect(Collectors.toList());
    }

    // 타이어 정보 수정 (상태 변경, 교체/폐기 처리 등)
    @Transactional
    public TireManagementResponse updateTire(Long tireId, TireManagementUpdateRequest request) {
        TireManagement tire = tireManagementRepository.findById(tireId)
                .orElseThrow(() -> new IllegalArgumentException("해당 타이어 정보를 찾을 수 없습니다. ID: " + tireId));

        if (request.getStatus() != null) {
            tire.setStatus(request.getStatus());
        }
        if (request.getDisposalDate() != null) {
            tire.setDisposalDate(request.getDisposalDate());
        }
        if (request.getDisposalMileage() != null) {
            tire.setDisposalMileage(request.getDisposalMileage());
        }
        if (request.getMemo() != null) {
            tire.setMemo(request.getMemo());
        }
        tire.setUpdatedAt(OffsetDateTime.now());

        return TireManagementResponse.from(tire);
    }

    /*
     * 10만 km 주기 알람 체크 로직
     * (타이어 장착 시점 대비 추가 주행거리가 10만 km를 돌파했는지 확인)
     */
    
    public String checkTireMilestone(Long tireId, int currentTotalMileage) {
        TireManagement tire = tireManagementRepository.findById(tireId)
                .orElseThrow(() -> new IllegalArgumentException("해당 타이어 정보를 찾을 수 없습니다. ID: " + tireId));

        int drivenDistance = currentTotalMileage - tire.getInstallationMileage();

        // 장착 후 주행거리가 10만 km 이상인 경우 알람 메시지 반환
        if (drivenDistance >= 100000) {
            return String.format("🚨 [알람] 차량(%s) %s 위치의 타이어가 장착 후 %d km를 주행하여 교체 주기(10만 km)에 도달했습니다!", 
                    tire.getVehicleNo(), tire.getAxlePosition(), drivenDistance);
        }

        return String.format("정상 (현재 장착 후 주행거리: %d km)", drivenDistance);
    }
}