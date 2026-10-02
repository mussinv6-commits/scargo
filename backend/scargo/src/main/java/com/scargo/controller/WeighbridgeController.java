package com.scargo.controller;

import com.scargo.dto.OverloadCheckResponse;
import com.scargo.dto.WeighbridgeQueueItem;
import com.scargo.dto.WeighingRequest;
import com.scargo.dto.WeighingResultResponse;
import com.scargo.dto.WeighbridgeRouteResponse;
import com.scargo.service.WeighbridgeRouteService;
import com.scargo.service.WeighbridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 26.10.01 추가(계중대 정식화): 계중대(검사소) 콘솔용 API
 *
 *   GET  /api/v1/weighbridge/policy                    판정 기준(축하중/총중량)과 계중대 코드
 *   GET  /api/v1/weighbridge/queue                     게이트 통과 후 계량 대기 중인 차량
 *   GET  /api/v1/weighbridge/weighings/today           오늘 계량 기록
 *   POST /api/v1/weighbridge/weighings                 계량 저장 (서버가 과적 판정)
 *   POST /api/v1/weighbridge/weighings/{id}/reweigh    감량 후 재계량
 */
@RestController
@RequestMapping("/api/v1/weighbridge")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
public class WeighbridgeController {

    private final WeighbridgeService weighbridgeService;
    private final WeighbridgeRouteService weighbridgeRouteService; // 26.10.02 추가: 지도 경로

    @GetMapping("/policy")
    public ResponseEntity<Map<String, Object>> getPolicy() {
        return ResponseEntity.ok(weighbridgeService.getPolicy());
    }

    @GetMapping("/queue")
    public ResponseEntity<List<WeighbridgeQueueItem>> getQueue() {
        return ResponseEntity.ok(weighbridgeService.getQueue());
    }

    // 26.10.01 추가: 차량번호로 등록차량 + 컨테이너 자동 조회 (직접 계량 / 재계량)
    @GetMapping("/vehicles/{vehicleNo}")
    public ResponseEntity<WeighbridgeQueueItem> getVehicleInfo(@PathVariable("vehicleNo") String vehicleNo) {
        return ResponseEntity.ok(weighbridgeService.getVehicleInfo(vehicleNo));
    }

    // 26.10.02 추가: 지도(OpenStreetMap)에 그릴 경로 - 진입 게이트 → 계중대 → 목적지
    @GetMapping("/route")
    public ResponseEntity<WeighbridgeRouteResponse> getRoute(
            @RequestParam("vehicleNo") String vehicleNo,
            @RequestParam(value = "gateLogId", required = false) Long gateLogId) {
        return ResponseEntity.ok(weighbridgeRouteService.getRoute(vehicleNo, gateLogId));
    }

    @GetMapping("/weighings/today")
    public ResponseEntity<List<OverloadCheckResponse>> getTodayRecords() {
        return ResponseEntity.ok(weighbridgeService.getTodayRecords());
    }

    @PostMapping("/weighings")
    public ResponseEntity<WeighingResultResponse> weigh(@Valid @RequestBody WeighingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(weighbridgeService.weigh(request));
    }

    @PostMapping("/weighings/{checkId}/reweigh")
    public ResponseEntity<WeighingResultResponse> reweigh(
            @PathVariable("checkId") Long checkId,
            @Valid @RequestBody WeighingRequest request) {
        return ResponseEntity.ok(weighbridgeService.reweigh(checkId, request));
    }
}
