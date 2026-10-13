package com.scargo.controller;

import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.GateLogResponse;
import com.scargo.dto.GateLogUpdateRequest;
import com.scargo.service.GateLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType; // 26.10.01 병합(태수님)
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile; // 26.10.01 병합(태수님)

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/gate-logs")
@RequiredArgsConstructor
public class GateLogController {

    private final GateLogService gateLogService;

    // 26.10.01 병합(태수님 26.09.30): 이미지 업로드 지원을 위해 생성 API를 JSON / multipart 두 가지로 분리
    //   (URL은 POST /api/v1/gate-logs 그대로, Content-Type으로 구분)

    // 1-A. 게이트 통과 이력 생성 (JSON 데이터만 단독으로 전송할 때)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<GateLogResponse> createGateLogFromJson(
            @Valid @RequestBody GateLogCreateRequest request) {

        // 이미지가 없는 상태로 서비스 호출 (frontImage, rearImage는 null 처리)
        GateLogResponse response = gateLogService.createGateLog(request, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 1-B. 게이트 통과 이력 생성 (JSON 데이터 + 앞/뒤 이미지 파일 함께 전송할 때)
    //   - data 파트: GateLogCreateRequest JSON (Content-Type: application/json)
    //   - frontImage / rearImage 파트: 이미지 파일 (선택)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<GateLogResponse> createGateLogFromMultipart(
            @Valid @RequestPart("data") GateLogCreateRequest request,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestPart(value = "rearImage", required = false) MultipartFile rearImage) {

        GateLogResponse response = gateLogService.createGateLog(request, frontImage, rearImage);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 게이트 통과 이력 단건 조회
    @GetMapping("/{gateLogId}")
    public ResponseEntity<GateLogResponse> getGateLog(@PathVariable("gateLogId") Long gateLogId) {
        GateLogResponse response = gateLogService.getGateLog(gateLogId);
        return ResponseEntity.ok(response);
    }

    // 3. 전체 게이트 통과 이력 조회 
    @GetMapping
    public ResponseEntity<Page<GateLogResponse>> getAllGateLogs(
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getAllGateLogs(pageable);
        return ResponseEntity.ok(response);
    }

    // 4. 차량 번호 기반 이력 검색
    @GetMapping("/search/vehicle")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsByVehicleNo(
            @RequestParam("actualVehicleNo") String actualVehicleNo,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsByActualVehicleNo(actualVehicleNo, pageable);
        return ResponseEntity.ok(response);
    }

    // 5. 게이트 타입(IN/OUT/BOTH)별 이력 검색
    @GetMapping("/search/type")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsByGateType(
            @RequestParam("gateType") String gateType,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsByGateType(gateType, pageable);
        return ResponseEntity.ok(response);
    }

    // 6. 기간별 이력 검색
    @GetMapping("/search/period")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsBetween(start, end, pageable);
        return ResponseEntity.ok(response);
    }

    // 7. 게이트 통과 이력 수동 수정 (관리자 권한 필요)
    @PatchMapping("/{gateLogId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<GateLogResponse> updateGateLog(
            @PathVariable("gateLogId") Long gateLogId,
            @Valid @RequestBody GateLogUpdateRequest request) { // ★ @Valid 어노테이션 추가
        GateLogResponse response = gateLogService.updateGateLog(gateLogId, request);
        return ResponseEntity.ok(response);
    }

    // 8. 게이트 통과 이력 삭제 (관리자 권한 필요)
    @DeleteMapping("/{gateLogId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGateLog(@PathVariable("gateLogId") Long gateLogId) {
        gateLogService.deleteGateLog(gateLogId);
        return ResponseEntity.noContent().build();
    }
}