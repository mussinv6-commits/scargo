package com.scargo.service;

import com.scargo.Enum.NotificationType; // 26.09.22 추가
import com.scargo.dto.NotificationCreateRequest; // 26.09.22 추가
import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.dto.TruckResponse;
import com.scargo.dto.TruckUpdateRequest;
import com.scargo.entity.Account; // 26.09.22 추가
import com.scargo.entity.Company;
import com.scargo.entity.Truck;
import com.scargo.repository.AccountRepository; // 26.09.22 추가
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
    private final AccountRepository accountRepository;     // 26.09.22 추가: 관리자/기사 계정 조회용
    private final NotificationService notificationService; // 26.09.22 추가: 차량 등록 시 관리자 알림 발송용

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

        // 26.09.22 추가: 차량이 등록되면 모든 관리자 계정에게 알림 발송
        notifyAdminsOfNewTruck(savedTruck, company);

        return TruckResponse.from(savedTruck);
    }

    // 26.09.22 추가: 신규 차량 등록을 관리자 전원에게 알림으로 전달
    private void notifyAdminsOfNewTruck(Truck truck, Company company) {
        List<Account> admins = accountRepository.findByUserType(Account.UserType.ADMIN);
        for (Account admin : admins) {
            NotificationCreateRequest notiRequest = NotificationCreateRequest.builder()
                    .accountId(admin.getAccountId())
                    .title("신규 차량 등록")
                    .message(company.getCompanyName() + " 업체에서 차량(" + truck.getVehicleNo() + ")을 새로 등록했습니다.")
                    .notificationType(NotificationType.NOTICE)
                    .build();
            notificationService.createNotification(notiRequest);
        }
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

    // ============================================================
    // 26.09.22 추가: 고정형(지입차) 기사-차량 배정
    // 1차량 : 1기사만 허용 (trucks.assigned_account_id UNIQUE 제약과 짝을 이룸)
    // ============================================================

    // 기사 배정 (관리자 또는 해당 업체 소속 사업자)
    // callerCompanyId: 관리자가 호출한 경우 null(제한 없음), 사업자가 호출한 경우 본인 companyId
    @Transactional
    public TruckResponse assignDriver(String vehicleNo, Long accountId, Long callerCompanyId) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        // 26.09.22 추가: 사업자가 호출한 경우, 본인 업체 소속 차량만 배정 가능
        if (callerCompanyId != null && !callerCompanyId.equals(truck.getCompanyId())) {
            throw new IllegalArgumentException("본인 업체 소속 차량만 기사 배정을 할 수 있습니다.");
        }

        Account driver = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        if (driver.getUserType() != Account.UserType.GENERAL) {
            throw new IllegalArgumentException("화물차 기사(일반회원) 계정만 배정할 수 있습니다.");
        }

        // 26.09.22 추가: 사업자가 호출한 경우, 본인 업체 소속 기사만 배정 가능
        if (callerCompanyId != null && !callerCompanyId.equals(driver.getCompanyId())) {
            throw new IllegalArgumentException("본인 업체 소속 기사만 배정할 수 있습니다.");
        }

        // 이미 다른 차량에 배정되어 있으면 막는다 (1기사 : 1차량 원칙)
        truckRepository.findByAssignedDriver_AccountId(accountId).ifPresent(existing -> {
            if (!existing.getVehicleNo().equals(vehicleNo)) {
                throw new IllegalArgumentException(
                        "[" + driver.getUserName() + "] 기사는 이미 다른 차량(" + existing.getVehicleNo() + ")에 배정되어 있습니다."
                );
            }
        });

        truck.setAssignedDriver(driver);
        return TruckResponse.from(truck);
    }

    // 기사 배정 해제 (관리자 또는 해당 업체 소속 사업자)
    @Transactional
    public TruckResponse unassignDriver(String vehicleNo, Long callerCompanyId) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        // 26.09.22 추가: 사업자가 호출한 경우, 본인 업체 소속 차량만 해제 가능
        if (callerCompanyId != null && !callerCompanyId.equals(truck.getCompanyId())) {
            throw new IllegalArgumentException("본인 업체 소속 차량만 배정 해제를 할 수 있습니다.");
        }

        truck.setAssignedDriver(null);
        return TruckResponse.from(truck);
    }

    // 로그인한 기사 본인에게 배정된 차량 조회 (없으면 null 반환)
    public TruckResponse getMyAssignedTruck(Long accountId) {
        return truckRepository.findByAssignedDriver_AccountId(accountId)
                .map(TruckResponse::from)
                .orElse(null);
    }

    // 26.09.22 추가: 관리자의 차량 진입 허가/불허 심사
    @Transactional
    public TruckResponse updateEntryApproval(String vehicleNo, String entryApproval) {
        Truck truck = truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다. 번호: " + vehicleNo));

        if (!java.util.List.of("PENDING", "APPROVED", "REJECTED").contains(entryApproval)) {
            throw new IllegalArgumentException("진입 허가 상태는 PENDING/APPROVED/REJECTED 중 하나여야 합니다.");
        }

        truck.updateEntryApproval(entryApproval);

        // 심사 결과를 차량 소속 업체(company_id 기준)에 알림으로 안내
        if ("APPROVED".equals(entryApproval) || "REJECTED".equals(entryApproval)) {
            String resultText = "APPROVED".equals(entryApproval) ? "허가" : "불허";
            NotificationCreateRequest notiRequest = NotificationCreateRequest.builder()
                    .companyId(truck.getCompanyId())
                    .title("차량 진입 심사 결과")
                    .message("등록하신 차량(" + truck.getVehicleNo() + ")의 진입이 " + resultText + "되었습니다.")
                    .notificationType(NotificationType.NOTICE)
                    .build();
            notificationService.createNotification(notiRequest);

            // 전담 기사가 배정돼 있으면 그 기사 개인에게도 알림
            if (truck.getAssignedDriver() != null) {
                NotificationCreateRequest driverNoti = NotificationCreateRequest.builder()
                        .accountId(truck.getAssignedDriver().getAccountId())
                        .title("차량 진입 심사 결과")
                        .message("내 차량(" + truck.getVehicleNo() + ")의 진입이 " + resultText + "되었습니다.")
                        .notificationType(NotificationType.NOTICE)
                        .build();
                notificationService.createNotification(driverNoti);
            }
        }

        return TruckResponse.from(truck);
    }
}