package com.scargo.controller;

import com.scargo.dto.ContainerOptionResponse;
import com.scargo.dto.MappingCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.service.MappingService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/mappings")
@RequiredArgsConstructor
public class MappingController {

    private final MappingService mappingService;

    // 세션 검증 공통 헬퍼 메서드
    private Long getLoginAccountId(HttpSession session) {
        Long accountId = (Long) session.getAttribute("accountId");
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return accountId;
    }

    //배정 가능한 컨테이너 목록 조회
    @GetMapping("/available-containers")
    public ResponseEntity<List<ContainerOptionResponse>> getAvailableContainers(HttpSession session) {
        Long accountId = getLoginAccountId(session);
        return ResponseEntity.ok(mappingService.getAvailableContainers(accountId));
    }

    // 소속 업체의 차량 목록 조회
    @GetMapping("/my-trucks")
    public ResponseEntity<List<TruckOptionResponse>> getCompanyTrucks(HttpSession session) {
        Long accountId = getLoginAccountId(session);
        return ResponseEntity.ok(mappingService.getCompanyTrucks(accountId));
    }

    // 차량-컨테이너 매핑(배정) 생성
    @PostMapping
    public ResponseEntity<Void> createMapping(
            HttpSession session,
            @Valid @RequestBody MappingCreateRequest request // @Valid 추가로 입력값 검증 자동화
    ) {
        Long accountId = getLoginAccountId(session);
        mappingService.createMapping(accountId, request);
        // 생성 리소스 성공 응답은 200 OK보다 201 Created 사용이 RESTful 표준에 적합
        return ResponseEntity.status(HttpStatus.CREATED).build(); 
    }

    // 차량-컨테이너 매핑(배정) 해제
    @DeleteMapping("/{containerNo}")
    public ResponseEntity<Void> cancelMapping(
            HttpSession session,
            @PathVariable String containerNo
    ) {
        Long accountId = getLoginAccountId(session);
        mappingService.cancelMapping(accountId, containerNo);
        // 삭제/해제 성공 시 내용 없음 응답(204 No Content) 적용
        return ResponseEntity.noContent().build(); 
    }
}