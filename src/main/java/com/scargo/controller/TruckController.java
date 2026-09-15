package com.scargo.controller;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckResponse;
import com.scargo.service.TruckService;
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

    // 차량 등록
    // 일반 회원도 입차시에 등록해야 하므로 따로 권한 부여 x
    @PostMapping
    public ResponseEntity<TruckResponse> createTruck(@RequestBody TruckCreateRequest request) {
        TruckResponse response = truckService.createTruck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 차량 목록 조회
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 차량목록 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<List<TruckResponse>> getAllTrucks() {
        List<TruckResponse> responses = truckService.getAllTrucks();
        return ResponseEntity.ok(responses);
    }

    // 특정 차량 단건 조회
    @GetMapping("/{vehicleNo}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 특정 차량 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<TruckResponse> getTruck(@PathVariable String vehicleNo) {
        TruckResponse response = truckService.getTruck(vehicleNo);
        return ResponseEntity.ok(response);
    }
}