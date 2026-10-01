package com.scargo.service;

import com.scargo.dto.VehicleChecklistCreateRequest;
import com.scargo.dto.VehicleChecklistResponse;
import com.scargo.dto.VehicleChecklistUpdateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.VehicleChecklist;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.VehicleChecklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleChecklistService {

    private final VehicleChecklistRepository vehicleChecklistRepository;
    private final AccountRepository accountRepository;

    // 인증 객체에서 로그인한 사용자의 회사 ID를 조회하는 헬퍼 메서드
    private Long getCompanyId(Authentication authentication) {
        String userId = authentication.getName();
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + userId));
        return account.getCompanyId();
    }

    // 전체 점검표 목록 통합 조회 (관리자는 모든 회사, 일반 기업은 소속 회사만 조회)
    public List<VehicleChecklistResponse> getAllChecklists(Authentication authentication, boolean isAdmin) {
        List<VehicleChecklist> checklists;

        if (isAdmin) {
            // 관리자는 전체 회사의 모든 점검표 조회
            checklists = vehicleChecklistRepository.findAllByOrderByInspectionDateDesc();
        } else {
            // 일반 기업 사용자는 우리 회사 소속 차량들의 점검표만 조회 (수정된 리포지토리 메서드 반영)
            Long companyId = getCompanyId(authentication);
            checklists = vehicleChecklistRepository.findAllByCompanyId(companyId);
        }

        return checklists.stream()
                .map(VehicleChecklistResponse::from)
                .collect(Collectors.toList());
    }

    // 일상점검표 등록 (소속 회사 차량 검증 포함)
    @Transactional
    public VehicleChecklistResponse createChecklist(VehicleChecklistCreateRequest request, Authentication authentication, boolean isAdmin) {
        
        // 관리자가 아니라면 로그인한 사용자의 회사 ID를 조회하여 차량 소속 검증
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean isOurVehicle = vehicleChecklistRepository.existsByVehicleNoAndCompanyId(request.getVehicleNo(), companyId);
            if (!isOurVehicle) {
                throw new IllegalArgumentException("우리 회사 소속 차량에만 점검표를 등록할 수 있습니다.");
            }
        }

        // DTO를 Entity로 변환 후 저장
        VehicleChecklist checklist = VehicleChecklist.builder()
                .vehicleNo(request.getVehicleNo())
                .inspectorAccountId(request.getInspectorAccountId())
                .inspectionDate(request.getInspectionDate())
                .lightStatus(request.getLightStatus())
                .brakeStatus(request.getBrakeStatus())
                .airBrakeStatus(request.getAirBrakeStatus())
                .tireStatus(request.getTireStatus())
                .steeringStatus(request.getSteeringStatus())
                .cargoSecurementStatus(request.getCargoSecurementStatus())
                .engineOilStatus(request.getEngineOilStatus())
                .seatbeltStatus(request.getSeatbeltStatus())
                .fireExtinguisherStatus(request.getFireExtinguisherStatus())
                .safetyTriangleStatus(request.getSafetyTriangleStatus())
                .overallStatus(request.getOverallStatus())
                .memo(request.getMemo())
                .build();

        VehicleChecklist savedChecklist = vehicleChecklistRepository.save(checklist);
        
        // Entity를 Response DTO로 변환하여 반환
        return VehicleChecklistResponse.from(savedChecklist);
    }

    // 일상점검표 단건 조회 (권한 검증 포함)
    public VehicleChecklistResponse getChecklistById(Long inspectionId, Authentication authentication, boolean isAdmin) {
        VehicleChecklist checklist = vehicleChecklistRepository.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점검 기록입니다. ID: " + inspectionId));

        // 관리자가 아니면 우리 회사 점검표인지 확인
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean hasPermission = vehicleChecklistRepository.existsByIdAndCompanyId(inspectionId, companyId);
            if (!hasPermission) {
                throw new SecurityException("다른 회사의 점검 기록에 접근할 수 없습니다.");
            }
        }

        return VehicleChecklistResponse.from(checklist);
    }

    // 특정 차량의 전체 점검 이력 조회 (권한 검증 포함)
    public List<VehicleChecklistResponse> getChecklistsByVehicleNo(String vehicleNo, Authentication authentication, boolean isAdmin) {
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean isOurVehicle = vehicleChecklistRepository.existsByVehicleNoAndCompanyId(vehicleNo, companyId);
            if (!isOurVehicle) {
                throw new SecurityException("다른 회사의 차량 정보는 조회할 수 없습니다.");
            }
        }

        return vehicleChecklistRepository.findByVehicleNoOrderByInspectionDateDesc(vehicleNo)
                .stream()
                .map(VehicleChecklistResponse::from)
                .collect(Collectors.toList());
    }

    // 특정 점검자가 작성한 전체 점검 이력 조회
    public List<VehicleChecklistResponse> getChecklistsByInspector(Long inspectorAccountId) {
        return vehicleChecklistRepository.findByInspectorAccountIdOrderByInspectionDateDesc(inspectorAccountId)
                .stream()
                .map(VehicleChecklistResponse::from)
                .collect(Collectors.toList());
    }

    // 특정 차량의 특정 일자 점검표 조회 (권한 검증 포함)
    public List<VehicleChecklistResponse> getChecklistsByVehicleAndDate(String vehicleNo, LocalDate inspectionDate, Authentication authentication, boolean isAdmin) {
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean isOurVehicle = vehicleChecklistRepository.existsByVehicleNoAndCompanyId(vehicleNo, companyId);
            if (!isOurVehicle) {
                throw new SecurityException("다른 회사의 차량 정보는 조회할 수 없습니다.");
            }
        }

        return vehicleChecklistRepository.findByVehicleNoAndInspectionDate(vehicleNo, inspectionDate)
                .stream()
                .map(VehicleChecklistResponse::from)
                .collect(Collectors.toList());
    }

    // 특정 차량의 특정 기간 점검표 조회 (권한 검증 포함)
    public List<VehicleChecklistResponse> getChecklistsByPeriod(String vehicleNo, LocalDate startDate, LocalDate endDate, Authentication authentication, boolean isAdmin) {
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean isOurVehicle = vehicleChecklistRepository.existsByVehicleNoAndCompanyId(vehicleNo, companyId);
            if (!isOurVehicle) {
                throw new SecurityException("다른 회사의 차량 정보는 조회할 수 없습니다.");
            }
        }

        return vehicleChecklistRepository.findByPeriod(vehicleNo, startDate, endDate)
                .stream()
                .map(VehicleChecklistResponse::from)
                .collect(Collectors.toList());
    }

    // 일상점검표 수정 (권한 검증 포함)
    @Transactional
    public VehicleChecklistResponse updateChecklist(Long inspectionId, VehicleChecklistUpdateRequest request, Authentication authentication, boolean isAdmin) {
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean hasPermission = vehicleChecklistRepository.existsByIdAndCompanyId(inspectionId, companyId);
            if (!hasPermission) {
                throw new SecurityException("다른 회사의 점검 기록은 수정할 수 없습니다.");
            }
        }

        VehicleChecklist checklist = vehicleChecklistRepository.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점검 기록입니다. ID: " + inspectionId));

        // 엔티티 필드 업데이트 (Dirty Checking 활용)
        checklist.update(
                request.getLightStatus(),
                request.getBrakeStatus(),
                request.getAirBrakeStatus(),
                request.getTireStatus(),
                request.getSteeringStatus(),
                request.getCargoSecurementStatus(),
                request.getEngineOilStatus(),
                request.getSeatbeltStatus(),
                request.getFireExtinguisherStatus(),
                request.getSafetyTriangleStatus(),
                request.getOverallStatus(),
                request.getMemo()
        );

        return VehicleChecklistResponse.from(checklist);
    }

    // 일상점검표 삭제 (권한 검증 포함)
    @Transactional
    public void deleteChecklist(Long inspectionId, Authentication authentication, boolean isAdmin) {
        if (!isAdmin) {
            Long companyId = getCompanyId(authentication);
            boolean hasPermission = vehicleChecklistRepository.existsByIdAndCompanyId(inspectionId, companyId);
            if (!hasPermission) {
                throw new SecurityException("다른 회사의 점검 기록은 삭제할 수 없습니다.");
            }
        }

        VehicleChecklist checklist = vehicleChecklistRepository.findById(inspectionId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점검 기록입니다. ID: " + inspectionId));
        vehicleChecklistRepository.delete(checklist);
    }
}