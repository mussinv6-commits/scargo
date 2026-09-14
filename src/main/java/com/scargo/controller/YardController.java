package com.scargo.controller;

import com.scargo.dto.YardCreateRequest;
import com.scargo.dto.YardResponse;
import com.scargo.service.YardService;
import com.scargo.dto.YardUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/yards")
@RequiredArgsConstructor
public class YardController {

    private final YardService yardService;

    // 야드 등록
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<YardResponse> createYard(@RequestBody YardCreateRequest request) {
        YardResponse response = yardService.createYard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 야드 목록 조회
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<List<YardResponse>> getAllYards() {
        List<YardResponse> yards = yardService.getAllYards();
        return ResponseEntity.ok(yards);
    }

    // 단건 야드 조회 (yardId 기준)
    @GetMapping("/{yardId}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<YardResponse> getYard(@PathVariable Long yardId) {
        YardResponse response = yardService.getYard(yardId);
        return ResponseEntity.ok(response);
    }

    // 야드 타입별 조회
    @GetMapping("/type/{yardType}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<List<YardResponse>> getYardsByType(@PathVariable String yardType) {
        List<YardResponse> yards = yardService.getYardsByType(yardType);
        return ResponseEntity.ok(yards);
    }

    // 사용 가능 여부별 조회 (예: /api/yards/availability?isAvailable=true)
    @GetMapping("/availability")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
    public ResponseEntity<List<YardResponse>> getYardsByAvailability(@RequestParam Boolean isAvailable) {
        List<YardResponse> yards = yardService.getYardsByAvailability(isAvailable);
        return ResponseEntity.ok(yards);
    }
    
    // 야드 수정
    @PutMapping("/{yardId}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 수정 가능, 필요시 주석 처리
    public ResponseEntity<YardResponse> updateYard(@PathVariable Long yardId, @RequestBody YardUpdateRequest request) {
        YardResponse response = yardService.updateYard(yardId, request);
        return ResponseEntity.ok(response);
    }
 
    
    // 야드 삭제
    @DeleteMapping("/{yardId}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 삭제 가능, 필요시 주석 처리
    public ResponseEntity<Void> deleteYard(@PathVariable Long yardId) {
        yardService.deleteYard(yardId);
        return ResponseEntity.noContent().build(); // 204 No Content 반
    }
}