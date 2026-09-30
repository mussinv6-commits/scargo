package com.scargo.controller;

import com.scargo.dto.ContainerCreateRequest;
import com.scargo.dto.ContainerResponse;
import com.scargo.dto.ContainerUpdateRequest;
import com.scargo.service.ContainerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;

    // 컨테이너 등록
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ContainerResponse> createContainer(@Valid @RequestBody ContainerCreateRequest request) {
        ContainerResponse response = containerService.createContainer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 컨테이너 목록 조회 (관리자 및 승인된 기업 계정)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<ContainerResponse>> getAllContainers() {
        List<ContainerResponse> responses = containerService.getAllContainers();
        return ResponseEntity.ok(responses);
    }

    // 특정 컨테이너 단건 조회 (관리자 및 승인된 기업 계정)
    @GetMapping("/{containerNo}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<ContainerResponse> getContainer(@PathVariable String containerNo) {
        ContainerResponse response = containerService.getContainer(containerNo);
        return ResponseEntity.ok(response);
    }

    // 컨테이너 정보 수정
    @PutMapping("/{containerNo}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ContainerResponse> updateContainer(
            @PathVariable String containerNo,
            @Valid @RequestBody ContainerUpdateRequest request) {
        ContainerResponse response = containerService.updateContainer(containerNo, request);
        return ResponseEntity.ok(response);
    }

    // 컨테이너 삭제
    @DeleteMapping("/{containerNo}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteContainer(@PathVariable String containerNo) {
        containerService.deleteContainer(containerNo);
        return ResponseEntity.noContent().build();
    }
}