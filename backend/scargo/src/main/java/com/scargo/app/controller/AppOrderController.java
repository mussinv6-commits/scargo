package com.scargo.app.controller;

import com.scargo.app.dto.AppDtos.*;
import com.scargo.app.security.CurrentAccount;
import com.scargo.app.service.TransportOrderService;
import com.scargo.app.service.TripMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 26.10.03 2차: 기사 앱 운송오더 API (화면 번호는 와이어프레임 기준)
 *
 *  GET  /api/app/home                         02 홈 (진행 중 오더 + 대기 오더 + 안 읽은 알림 수)
 *  GET  /api/app/orders/{id}                  오더 상세
 *  POST /api/app/orders/lookup                03 차량번호+컨테이너번호 → 자동 입력
 *  POST /api/app/orders/{id}/precheck         04 출발 전 점검
 *  POST /api/app/orders/{id}/depart           05 운행 시작 (경로 계산)
 *  GET  /api/app/orders/{id}/route            05 경로 + 영업소/검문소/높이제한
 *  POST /api/app/orders/{id}/reroute          05 경로 이탈 → 현재 위치에서 재탐색 (60초에 1번)
 *  POST /api/app/orders/{id}/locations        05 위치 전송 → 남은 거리/ETA/경고
 *  GET  /api/app/orders/{id}/trip-status      05 위치 전송 없이 상태만
 *  POST /api/app/orders/{id}/arrive           06 수동 도착
 *  POST /api/app/orders/{id}/handover         06 인계 (multipart: 사진, 서명)
 *  GET  /api/app/orders/history?range=today|week|month   07 운송 이력
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppOrderController {

    private final TransportOrderService orderService;
    private final TripMonitorService tripMonitorService;

    @GetMapping("/home")
    public HomeResponse home() {
        return orderService.home(CurrentAccount.id());
    }

    @GetMapping("/orders/history")
    public HistoryResponse history(@RequestParam(value = "range", defaultValue = "today") String range) {
        return orderService.history(CurrentAccount.id(), range);
    }

    @GetMapping("/orders/{id}")
    public OrderDetail detail(@PathVariable("id") Long id) {
        return orderService.getDetail(CurrentAccount.id(), id);
    }

    @PostMapping("/orders/lookup")
    public OrderDetail lookup(@RequestBody LookupRequest request) {
        return orderService.lookup(CurrentAccount.id(), request);
    }

    @PostMapping("/orders/{id}/precheck")
    public PrecheckResponse precheck(@PathVariable("id") Long id, @RequestBody PrecheckRequest request) {
        return orderService.precheck(CurrentAccount.id(), id, request);
    }

    @PostMapping("/orders/{id}/depart")
    public OrderDetail depart(@PathVariable("id") Long id, @RequestBody(required = false) DepartRequest request) {
        return orderService.depart(CurrentAccount.id(), id, request);
    }

    @GetMapping("/orders/{id}/route")
    public RouteResponse route(@PathVariable("id") Long id) {
        return orderService.route(CurrentAccount.id(), id);
    }

    @PostMapping("/orders/{id}/reroute")
    public RouteResponse reroute(@PathVariable("id") Long id, @RequestBody RerouteRequest request) {
        return orderService.reroute(CurrentAccount.id(), id, request);
    }

    @PostMapping("/orders/{id}/locations")
    public TripStatus location(@PathVariable("id") Long id, @RequestBody LocationRequest request) {
        return tripMonitorService.ingest(CurrentAccount.id(), id, request);
    }

    @GetMapping("/orders/{id}/trip-status")
    public TripStatus tripStatus(@PathVariable("id") Long id) {
        return tripMonitorService.status(CurrentAccount.id(), id);
    }

    @PostMapping("/orders/{id}/arrive")
    public OrderDetail arrive(@PathVariable("id") Long id) {
        return orderService.arrive(CurrentAccount.id(), id);
    }

    @PostMapping(value = "/orders/{id}/handover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HandoverResult handover(@PathVariable("id") Long id,
                                   @RequestParam(value = "containerNoConfirmed", defaultValue = "false") boolean containerNoConfirmed,
                                   @RequestParam(value = "sealOk", defaultValue = "false") boolean sealOk,
                                   @RequestParam(value = "damageFree", defaultValue = "false") boolean damageFree,
                                   @RequestParam(value = "sealNo", required = false) String sealNo,
                                   @RequestParam(value = "receiverName", required = false) String receiverName,
                                   @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
                                   @RequestPart(value = "signature", required = false) MultipartFile signature) {
        // 26.10.21 씰번호 필수 (빈 값·형식 오류 → 400)
        if (sealNo == null || sealNo.isBlank()) throw new IllegalArgumentException("씰번호를 입력하세요");
        if (!sealNo.trim().matches(com.scargo.ops.dto.SealRequests.SEAL_REGEX)) throw new IllegalArgumentException(com.scargo.ops.dto.SealRequests.SEAL_MSG);
        return orderService.handover(CurrentAccount.id(), id, containerNoConfirmed, sealOk, damageFree,
                com.scargo.ops.dto.SealRequests.normalize(sealNo), receiverName, photos, signature);
    }
}
