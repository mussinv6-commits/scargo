package com.scargo.controller;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.dto.TruckResponse;
import com.scargo.dto.TruckUpdateRequest;
import com.scargo.entity.LoadingRecord;
import com.scargo.entity.Truck;
import com.scargo.entity.Container;
import com.scargo.repository.ContainerRepository;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;
import com.scargo.service.TruckService;
import com.scargo.service.TruckWorkflowService;
import com.scargo.security.AuthenticatedAccountPrincipal;
import com.scargo.security.TruckAccessPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@RequiredArgsConstructor
public class TruckController {

    private final TruckService truckService;
    private final TruckAccessPolicy truckAccessPolicy;
    private final TruckWorkflowService truckWorkflowService;       // 26.10.01 병합(마무리본)
    private final TruckRepository truckRepository;                 // 26.10.01 병합(마무리본)
    private final LoadingRecordRepository loadingRecordRepository; // 26.10.01 병합(마무리본)
    private final ContainerRepository containerRepository;         // 26.10.22 추가: CANCELED 사유 구분 (매핑 해제)
    private final OverloadCheckRepository overloadCheckRepository; // 26.10.22 추가: CANCELED 사유 구분 (과적 재검사 제한)

    // TruckWorkflowService.processFinalOcr 의 자동 출차 불가 기준과 같은 값
    private static final int OVERLOAD_RETRY_LIMIT = 3;

    // 차량 등록
    // 관리자 또는 소속 업체의 운송사 계정만 등록 가능
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> createTruck(@Valid @RequestBody TruckCreateRequest request, Authentication authentication) {
        truckAccessPolicy.requireCompanyManagement(authentication, request.getCompanyId());
        TruckResponse response = truckService.createTruck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 역할별 관리 범위의 차량 목록 조회
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<TruckResponse>> getAllTrucks(Authentication authentication) {
        List<TruckResponse> responses = truckAccessPolicy.readableTrucks(authentication, null).stream()
                .map(TruckResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // 특정 차량 단건 상세 조회
    @GetMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> getTruck(@PathVariable("vehicleNo") String vehicleNo, Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, vehicleNo);
        TruckResponse response = truckService.getTruck(vehicleNo);
        return ResponseEntity.ok(response);
    }

    // 업체별 드롭다운/선택용 경량 차량 옵션 목록 조회
    // 예시 경로: GET /api/trucks/options?companyId=1
    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<TruckOptionResponse>> getTruckOptions(@RequestParam("companyId") Long companyId, Authentication authentication) {
        List<TruckOptionResponse> responses = truckAccessPolicy.readableTrucks(authentication, companyId).stream()
                .map(TruckOptionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // 차량 전체/부분 정보 수정
    @PutMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> updateTruck(
            @PathVariable("vehicleNo") String vehicleNo,
            @Valid @RequestBody TruckUpdateRequest request,
            Authentication authentication) {
        truckAccessPolicy.requireTruckManagement(authentication, vehicleNo);
        if (request.getStatus() != null) {
            throw new AccessDeniedException("차량 상태는 기준정보 수정으로 변경할 수 없습니다. 관제 상태 변경 API를 사용해주세요.");
        }
        if (request.getCompanyId() != null) {
            truckAccessPolicy.requireCompanyManagement(authentication, request.getCompanyId());
        }
        TruckResponse response = truckService.updateTruck(vehicleNo, request);
        return ResponseEntity.ok(response);
    }

    // 차량 상태만 단독 변경 (OUTSIDE, INSIDE, IN_TRANSIT 등)
    // 예시 경로: PATCH /api/trucks/12가3456/status?status=INSIDE
    @PatchMapping("/{vehicleNo}/status")
    @PreAuthorize("hasRole('CONTROL')")
    public ResponseEntity<TruckResponse> updateTruckStatus(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("status") String status,
            Authentication authentication) {
        truckAccessPolicy.requireControl(authentication, vehicleNo);
        if (!List.of("OUTSIDE", "INSIDE", "IN_TRANSIT").contains(status)) {
            throw new IllegalArgumentException("차량 상태는 OUTSIDE/INSIDE/IN_TRANSIT 중 하나여야 합니다.");
        }
        TruckResponse response = truckService.updateTruckStatus(vehicleNo, status);
        return ResponseEntity.ok(response);
    }

    // 차량 정보 삭제
    @DeleteMapping("/{vehicleNo}")
    @PreAuthorize("hasRole('ADMIN')") // 관리자 전용
    public ResponseEntity<Void> deleteTruck(@PathVariable("vehicleNo") String vehicleNo) {
        truckService.deleteTruck(vehicleNo);
        return ResponseEntity.noContent().build();
    }

    // 26.09.22 추가: 고정형(지입차) 기사 배정/해제/본인조회 -----------------------

    // 기사 배정 변경은 자기 업체의 운송사 담당자만 허용
    // HUB_OP는 배차 조회만 허용하며 ADMIN은 운영 배차를 수행하지 않음
    // 예: PATCH /api/trucks/12가3456/assign-driver?accountId=5
    @PatchMapping("/{vehicleNo}/assign-driver")
    @PreAuthorize("hasRole('CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> assignDriver(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("accountId") Long accountId,
            Authentication authentication) {
        TruckResponse response = truckService.assignDriver(vehicleNo, accountId, truckAccessPolicy.dispatchCompany(authentication));
        return ResponseEntity.ok(response);
    }

    // 자기 업체 차량의 기사 배정 해제
    @PatchMapping("/{vehicleNo}/unassign-driver")
    @PreAuthorize("hasRole('CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> unassignDriver(
            @PathVariable("vehicleNo") String vehicleNo,
            Authentication authentication) {
        TruckResponse response = truckService.unassignDriver(vehicleNo, truckAccessPolicy.dispatchCompany(authentication));
        return ResponseEntity.ok(response);
    }

    // 26.09.22 추가: 관리자의 차량 진입 허가/불허 심사
    // 예: PATCH /api/trucks/12가3456/entry-approval?entryApproval=APPROVED
    @PatchMapping("/{vehicleNo}/entry-approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TruckResponse> updateEntryApproval(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("entryApproval") String entryApproval) {
        TruckResponse response = truckService.updateEntryApproval(vehicleNo, entryApproval);
        return ResponseEntity.ok(response);
    }

    // 로그인한 기사 본인에게 배정된 차량 조회 (없으면 204 No Content)
    // 26.10.22 통합: 회원가입 일반회원(GENERAL)도 기사 앱을 열 수 있게 허용 (본인 배정 차량만 조회, DB상 배정이 없어 204)
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'GENERAL')")
    public ResponseEntity<TruckResponse> getMyAssignedTruck(
            Authentication authentication) {
        AuthenticatedAccountPrincipal principal =
                (AuthenticatedAccountPrincipal) authentication.getPrincipal();
        TruckResponse response = truckService.getMyAssignedTruck(principal.getId());
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    // 26.10.01 병합(마무리본): OCR 차량번호와 현재 상하차 상태를 확인하여 입차 또는 출차 워크플로우 실행
    // 예: POST /api/trucks/ocr?vehicleNo=12가3456
    @PostMapping("/ocr")
    @PreAuthorize("hasRole('CONTROL')")
    public ResponseEntity<String> receiveOcrEvent(@RequestParam("vehicleNo") String vehicleNo, Authentication authentication) {
        truckAccessPolicy.requireControl(authentication, vehicleNo);

        // OCR 차량번호로 등록 차량 조회
        Truck truck = truckRepository.findByVehicleNo(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 차량입니다: " + vehicleNo));

        String currentStatus = truck.getStatus();

        // 해당 차량의 가장 최근 상하차 기록 조회
        LoadingRecord loadingRecord = loadingRecordRepository.findFirstByTruck_VehicleNoOrderByRecordIdDesc(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("상하차 기록이 없습니다: " + vehicleNo));

        LoadingRecord.LoadingStatus loadingStatus = loadingRecord.getStatus();

        // OUTSIDE + PENDING 상태이면 첫 번째 OCR 입차 처리
        if ("OUTSIDE".equalsIgnoreCase(currentStatus) && loadingStatus == LoadingRecord.LoadingStatus.PENDING) {
            truckWorkflowService.processFirstOcr(vehicleNo, loadingRecord.getRecordId());
            return ResponseEntity.ok("트럭(" + vehicleNo + ") 진입 OCR 인식: 첫 번째 워크플로우가 시작되었습니다.");
        }

        // IN_PROGRESS 상태이면 상하차 작업 진행 중이므로 최종 OCR 처리 금지
        if (loadingStatus == LoadingRecord.LoadingStatus.IN_PROGRESS) {
            return ResponseEntity.badRequest().body(
                    "트럭(" + vehicleNo + ")은 현재 상하차 작업이 진행 중이므로 출차 OCR 처리를 할 수 없습니다.");
        }

        // INSIDE + COMPLETED 상태이면 최종 OCR 출차 처리
        if ("INSIDE".equalsIgnoreCase(currentStatus) && loadingStatus == LoadingRecord.LoadingStatus.COMPLETED) {
            truckWorkflowService.processFinalOcr(vehicleNo);
            return ResponseEntity.ok("트럭(" + vehicleNo + ") 출차 OCR 인식: 최종 과적 검증 처리가 실행되었습니다.");
        }

        // CANCELED 상태이면 자동 처리하지 않음 (응답 400 · 상태 변경 없음은 기존과 같음)
        // 26.10.22 수정: CANCELED 는 매핑 해제로도 생기므로 사유를 구분해 안내한다
        if (loadingStatus == LoadingRecord.LoadingStatus.CANCELED) {
            return ResponseEntity.badRequest().body(canceledLoadingMessage(vehicleNo, loadingRecord));
        }

        // 기존 OCR 워크플로우: IN_TRANSIT 상태에서는 최종 OCR 처리 금지
        if ("IN_TRANSIT".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest().body(
                    "트럭(" + vehicleNo + ")은 현재 IN_TRANSIT 상태이므로 출차 OCR 처리를 할 수 없습니다.");
        }

        // 정의되지 않은 차량 상태와 상하차 상태 조합 처리
        return ResponseEntity.badRequest().body(
                "OCR 처리 불가 상태입니다. 차량 상태=" + currentStatus + ", 상하차 상태=" + loadingStatus);
    }

    // 26.10.22 추가: 최근 상하차 기록이 CANCELED 인 이유별 안내 (조회만 하고 상태는 바꾸지 않음)
    //  1) 그 기록의 컨테이너가 더 이상 이 차량에 배정돼 있지 않음 → 매핑 해제 · 변경으로 취소된 작업
    //  2) 이 차량의 최근 과적 검사가 재검사 제한(3회) 이상 → 기존 안내 그대로 (관리자 수동 출차)
    //  3) 그 밖의 수동 취소 → 관리자 확인 안내
    private String canceledLoadingMessage(String vehicleNo, LoadingRecord loadingRecord) {
        Container container = loadingRecord.getContainer();
        boolean stillMapped = container != null && containerRepository.findById(container.getContainerNo())
                .map(c -> vehicleNo.equals(c.getAssignedVehicleNo()))
                .orElse(false);
        if (!stillMapped) {
            return "트럭(" + vehicleNo + ")은 컨테이너 매핑이 해제되어 상하차 작업이 취소된 차량입니다. "
                    + "새 컨테이너를 배정한 뒤 다시 처리해주세요.";
        }

        boolean retryLimitExceeded = overloadCheckRepository.findTopByVehicleNoOrderByCheckedAtDesc(vehicleNo)
                .map(check -> check.getRetryCount() != null && check.getRetryCount() >= OVERLOAD_RETRY_LIMIT)
                .orElse(false);
        if (retryLimitExceeded) {
            return "트럭(" + vehicleNo + ")은 과적 재검사 제한 초과 차량으로 관리자 수동 출차가 필요합니다.";
        }

        return "트럭(" + vehicleNo + ")은 상하차 작업이 취소된 차량입니다. 관리자 확인이 필요합니다.";
    }
}
