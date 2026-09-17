package com.scargo.controller;

import com.scargo.dto.*;
import com.scargo.service.MappingService;
import jakarta.servlet.http.HttpSession;
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

    // 세션 검증 공통 메서드
    private Long getLoginAccountId(HttpSession session) {
        Long accountId = (Long) session.getAttribute("accountId");
        if (accountId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return accountId;
    }

    @GetMapping("/available-containers")
    public ResponseEntity<List<ContainerOptionResponse>> getAvailableContainers(HttpSession session) {
        Long accountId = getLoginAccountId(session);
        return ResponseEntity.ok(mappingService.getAvailableContainers(accountId));
    }

    @GetMapping("/my-trucks")
    public ResponseEntity<List<TruckOptionResponse>> getCompanyTrucks(HttpSession session) {
        Long accountId = getLoginAccountId(session);
        return ResponseEntity.ok(mappingService.getCompanyTrucks(accountId));
    }

    @PostMapping
    public ResponseEntity<Void> createMapping(
            HttpSession session,
            @RequestBody MappingCreateRequest request
    ) {
        Long accountId = getLoginAccountId(session);
        mappingService.createMapping(accountId, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{containerNo}")
    public ResponseEntity<Void> cancelMapping(
            HttpSession session,
            @PathVariable String containerNo
    ) {
        Long accountId = getLoginAccountId(session);
        mappingService.cancelMapping(accountId, containerNo);
        return ResponseEntity.ok().build();
    }
}