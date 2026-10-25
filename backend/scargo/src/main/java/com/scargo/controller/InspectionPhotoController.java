package com.scargo.controller;

import com.scargo.service.InspectionPhotoService;
import com.scargo.service.InspectionPhotoService.PhotoFile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// 26.10.24 점검표 손상 사진 (권한은 InspectionPhotoService 가 점검표와 같은 규칙으로 확인)
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/container-inspections/{id}/photos")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'HUB_OP')")
public class InspectionPhotoController {

    private final InspectionPhotoService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(Authentication authentication, @PathVariable("id") Long id) {
        return ResponseEntity.ok(service.list(authentication, id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            Authentication authentication, @PathVariable("id") Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.upload(authentication, id, file));
    }

    @GetMapping("/{photoId}/file")
    public ResponseEntity<byte[]> file(
            Authentication authentication, @PathVariable("id") Long id, @PathVariable("photoId") Long photoId) {
        PhotoFile f = service.file(authentication, id, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .cacheControl(CacheControl.noStore())
                .body(f.bytes());
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(
            Authentication authentication, @PathVariable("id") Long id, @PathVariable("photoId") Long photoId) {
        service.delete(authentication, id, photoId);
        return ResponseEntity.noContent().build();
    }
}
