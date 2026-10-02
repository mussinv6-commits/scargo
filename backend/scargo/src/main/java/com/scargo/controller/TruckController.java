package com.scargo.controller;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.dto.TruckResponse;
import com.scargo.dto.TruckUpdateRequest;
import com.scargo.entity.LoadingRecord; // 26.10.01 병합(마무리본): OCR 입출차 워크플로우
import com.scargo.entity.Truck;
import com.scargo.repository.LoadingRecordRepository;
import com.scargo.repository.TruckRepository;
import com.scargo.service.TruckService;
import com.scargo.service.TruckWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@RequiredArgsConstructor
public class TruckController {

    private final TruckService truckService;
    private final TruckWorkflowService truckWorkflowService;       // 26.10.01 병합(마무리본)
    private final TruckRepository truckRepository;                 // 26.10.01 병합(마무리본)
    private final LoadingRecordRepository loadingRecordRepository; // 26.10.01 병합(마무리본)

    // 차량 등록
    // 일반 회원도 입차 시 등록 필요할 수 있으므로 별도 역할 제한 X
    @PostMapping
    public ResponseEntity<TruckResponse> createTruck(@Valid @RequestBody TruckCreateRequest request) {
        TruckResponse response = truckService.createTruck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 차량 목록 조회
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<List<TruckResponse>> getAllTrucks() {
        List<TruckResponse> responses = truckService.getAllTrucks();
        return ResponseEntity.ok(responses);
    }

    // 특정 차량 단건 상세 조회
    @GetMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<TruckResponse> getTruck(@PathVariable("vehicleNo") String vehicleNo) {
        TruckResponse response = truckService.getTruck(vehicleNo);
        return ResponseEntity.ok(response);
    }

    // 업체별 드롭다운/선택용 경량 차량 옵션 목록 조회
    // 예시 경로: GET /api/trucks/options?companyId=1
    @GetMapping("/options")
    public ResponseEntity<List<TruckOptionResponse>> getTruckOptions(@RequestParam("companyId") Long companyId) {
        List<TruckOptionResponse> responses = truckService.getTruckOptions(companyId);
        return ResponseEntity.ok(responses);
    }

    // 차량 전체/부분 정보 수정
    @PutMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<TruckResponse> updateTruck(
            @PathVariable("vehicleNo") String vehicleNo,
            @Valid @RequestBody TruckUpdateRequest request) {
        TruckResponse response = truckService.updateTruck(vehicleNo, request);
        return ResponseEntity.ok(response);
    }

    // 차량 상태만 단독 변경 (OUTSIDE, INSIDE, IN_TRANSIT 등)
    // 예시 경로: PATCH /api/trucks/12가3456/status?status=INSIDE
    @PatchMapping("/{vehicleNo}/status")
    public ResponseEntity<TruckResponse> updateTruckStatus(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("status") String status) {
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

    // 26.09.22 수정: 기사 배정은 관리자뿐 아니라 해당 업체 소속 사업자도 할 수 있게 완화
    // (관리자는 전체 대상, 사업자는 본인 업체 범위로 서비스 계층에서 제한)
    // 예: PATCH /api/trucks/12가3456/assign-driver?accountId=5
    @PatchMapping("/{vehicleNo}/assign-driver")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> assignDriver(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("accountId") Long accountId,
            org.springframework.security.core.Authentication authentication) {
        TruckResponse response = truckService.assignDriver(vehicleNo, accountId, callerCompanyIdOrNull(authentication));
        return ResponseEntity.ok(response);
    }

    // 특정 차량의 기사 배정 해제 (관리자 또는 해당 업체 소속 사업자)
    @PatchMapping("/{vehicleNo}/unassign-driver")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<TruckResponse> unassignDriver(
            @PathVariable("vehicleNo") String vehicleNo,
            org.springframework.security.core.Authentication authentication) {
        TruckResponse response = truckService.unassignDriver(vehicleNo, callerCompanyIdOrNull(authentication));
        return ResponseEntity.ok(response);
    }

    // 26.09.22 추가: 관리자면 제한 없음(null), 사업자면 본인 companyId로 제한
    private Long callerCompanyIdOrNull(org.springframework.security.core.Authentication authentication) {
        com.scargo.security.AuthenticatedAccountPrincipal principal =
                (com.scargo.security.AuthenticatedAccountPrincipal) authentication.getPrincipal();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? null : principal.getCompanyId();
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
    @GetMapping("/my")
    @PreAuthorize("hasRole('GENERAL')")
    public ResponseEntity<TruckResponse> getMyAssignedTruck(
            org.springframework.security.core.Authentication authentication) {
        com.scargo.security.AuthenticatedAccountPrincipal principal =
                (com.scargo.security.AuthenticatedAccountPrincipal) authentication.getPrincipal();
        TruckResponse response = truckService.getMyAssignedTruck(principal.getId());
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.noContent().build();
    }

    // 26.10.01 병합(마무리본): OCR 차량번호와 현재 상하차 상태를 확인하여 입차 또는 출차 워크플로우 실행
    // 예: POST /api/trucks/ocr?vehicleNo=12가3456
    @PostMapping("/ocr")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> receiveOcrEvent(@RequestParam("vehicleNo") String vehicleNo) {

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

        // CANCELED 상태이면 과적 재검사 제한 초과 차량이므로 자동 출차 금지
        if (loadingStatus == LoadingRecord.LoadingStatus.CANCELED) {
            return ResponseEntity.badRequest().body(
                    "트럭(" + vehicleNo + ")은 과적 재검사 제한 초과 차량으로 관리자 수동 출차가 필요합니다.");
        }

        // IN_TRANSIT 상태이면 야드 내부 이동 중이므로 최종 OCR 처리 금지
        if ("IN_TRANSIT".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest().body(
                    "트럭(" + vehicleNo + ")은 현재 IN_TRANSIT 상태이므로 출차 OCR 처리를 할 수 없습니다.");
        }

        // 정의되지 않은 차량 상태와 상하차 상태 조합 처리
        return ResponseEntity.badRequest().body(
                "OCR 처리 불가 상태입니다. 차량 상태=" + currentStatus + ", 상하차 상태=" + loadingStatus);
    }
}