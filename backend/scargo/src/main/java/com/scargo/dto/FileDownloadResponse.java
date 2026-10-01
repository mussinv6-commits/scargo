package com.scargo.dto;

import lombok.Builder;
import lombok.Getter;
import org.springframework.core.io.Resource;

@Getter
@Builder
public class FileDownloadResponse {
    private Resource resource;          // 실제 파일 리소스 (스트리밍용)
    private String originalFileName;    // 원본 파일명 (다운로드 시 표시될 이름)
    private String contentType;         // 파일 타입 (예: image/png, application/pdf 등)
}