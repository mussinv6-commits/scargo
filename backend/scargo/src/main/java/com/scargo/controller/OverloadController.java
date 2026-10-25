package com.scargo.controller;

import com.scargo.dto.OverloadCheckCreateRequest;
import com.scargo.dto.OverloadCheckResponse;
import com.scargo.dto.OverloadCheckUpdateRequest;
import com.scargo.entity.Account;
import com.scargo.security.TruckAccessPolicy;
import com.scargo.service.OverloadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/overload-checks")
@RequiredArgsConstructor
public class OverloadController {

    private final OverloadService overloadService;
    private final TruckAccessPolicy truckAccessPolicy; // 26.10.22 추가: 차량 관리 범위 검사

    // 1. 과적 검사 기록 생성 (계측 장비/시스템 입력)
    // 26.10.22 수정: 권한 검사 추가 (기존에는 로그인만 하면 누구나 생성 가능). 관리 범위의 차량만
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED')")
    public ResponseEntity<OverloadCheckResponse> createCheck(@Valid @RequestBody OverloadCheckCreateRequest request, Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, request.getVehicleNo());
        OverloadCheckResponse response = overloadService.createCheck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 과적 검사 전체 목록 조회 (페이징, 관리자 전용)
    // 예시: GET /api/overload-checks?page=0&size=10&sort=checkId,desc
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<Page<OverloadCheckResponse>> getAllChecks(
            @PageableDefault(size = 10, sort = "checkId", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<OverloadCheckResponse> responses = overloadService.getAllChecks(companyScope(authentication), pageable);
        return ResponseEntity.ok(responses);
    }

    // 3. 과적 검사 기록 단건 상세 조회
    @GetMapping("/{checkId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<OverloadCheckResponse> getCheckById(@PathVariable("checkId") Long checkId, Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, overloadService.getVehicleNo(checkId));
        OverloadCheckResponse response = overloadService.getCheckById(checkId);
        return ResponseEntity.ok(response);
    }

    // 4. 특정 차량의 과적 검사 이력 조회 (페이징 및 키워드 검색)
    // 예시: GET /api/overload-checks/vehicle?vehicleNo=12가3456
    @GetMapping("/vehicle")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<Page<OverloadCheckResponse>> getChecksByVehicleNo(
            @RequestParam("vehicleNo") String vehicleNo,
            @PageableDefault(size = 10, sort = "checkId", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, vehicleNo);
        Page<OverloadCheckResponse> responses = overloadService.getChecksByVehicleNo(vehicleNo, pageable);
        return ResponseEntity.ok(responses);
    }

    // 5. 위반(과적) 차량 목록 조회
    // 예시: GET /api/overload-checks/violations
    @GetMapping("/violations")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<Page<OverloadCheckResponse>> getViolationChecks(
            @PageableDefault(size = 10, sort = "checkId", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<OverloadCheckResponse> responses = overloadService.getViolationChecks(companyScope(authentication), pageable);
        return ResponseEntity.ok(responses);
    }

    // 6. 불합격 차량 목록 조회
    // 예시: GET /api/overload-checks/failed
    @GetMapping("/failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<Page<OverloadCheckResponse>> getFailedChecks(
            @PageableDefault(size = 10, sort = "checkId", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<OverloadCheckResponse> responses = overloadService.getFailedChecks(companyScope(authentication), pageable);
        return ResponseEntity.ok(responses);
    }

    // 7. 기간별 과적 검사 이력 조회
    // 예시: GET /api/overload-checks/period?start=2026-01-01T00:00:00+09:00&end=2026-12-31T23:59:59+09:00
    @GetMapping("/period")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<Page<OverloadCheckResponse>> getChecksBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start, // 26.10.01 병합(태수님): 파라미터 이름 명시
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @PageableDefault(size = 10, sort = "checkId", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        Page<OverloadCheckResponse> responses = overloadService.getChecksBetween(companyScope(authentication), start, end, pageable);
        return ResponseEntity.ok(responses);
    }

    // 8. 과적 감량 후 재검증(Retry) 처리
    // 예시: POST /api/overload-checks/1/retry
    // 26.10.22 수정: 원래 차량과 요청의 차량이 모두 관리 범위여야 함 (재검증 요청으로 차량번호를 바꿀 수 있음)
    @PostMapping("/{checkId}/retry")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED')")
    public ResponseEntity<OverloadCheckResponse> retryCheck(
            @PathVariable("checkId") Long checkId,
            @Valid @RequestBody OverloadCheckCreateRequest request,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, overloadService.getVehicleNo(checkId));
        if (request.getVehicleNo() != null) {
            truckAccessPolicy.requireRead(authentication, request.getVehicleNo());
        }
        OverloadCheckResponse response = overloadService.retryCheck(checkId, request);
        return ResponseEntity.ok(response);
    }

    // 9. 과적 검사 기록 관리자 오기 정정용 (수정)
    @PutMapping("/{checkId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OverloadCheckResponse> updateCheck(
            @PathVariable("checkId") Long checkId,
            @Valid @RequestBody OverloadCheckUpdateRequest request) {
        OverloadCheckResponse response = overloadService.updateCheck(checkId, request);
        return ResponseEntity.ok(response);
    }

    // 10. 과적 검사 기록 삭제 (관리자 전용)
    @DeleteMapping("/{checkId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCheck(@PathVariable("checkId") Long checkId) {
        overloadService.deleteCheck(checkId);
        return ResponseEntity.noContent().build();
    }

    // 26.10.22 추가: 목록 조회 범위. 관리자는 전체(null), 운송사 담당자는 자기 업체
    private Long companyScope(Authentication authentication) {
        Account account = truckAccessPolicy.currentAccount(authentication);
        if (account.getUserType() == Account.UserType.ADMIN) {
            return null;
        }
        if (account.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 있는 계정만 조회할 수 있습니다.");
        }
        return account.getCompanyId();
    }
}
