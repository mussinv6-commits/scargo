package com.scargo.controller;

import com.scargo.dto.FileDownloadResponse;
import com.scargo.dto.PostCreateRequest;
import com.scargo.dto.PostResponse;
import com.scargo.dto.PostUpdateRequest;
import com.scargo.entity.Post.PostCategory;
import com.scargo.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

// 게시글 CRUD 및 검색 API 컨트롤러
@RestController
@RequestMapping({"/api/v1/posts", "/bbs"}) // /bbs 경로도 받을 수 있도록 추가
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    // 게시글 작성 (관리자만 가능)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')") // 26.10.01 병합: hasAuthority('ADMIN') → hasRole('ADMIN') (세션 필터가 ROLE_ADMIN 으로 권한을 주기 때문에 기존 표기로는 관리자도 403)
    public ResponseEntity<PostResponse> createPost(
            @RequestParam("accountId") Long accountId,
            @Valid @ModelAttribute PostCreateRequest request) {
        
        PostResponse response = postService.createPost(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 게시글 상세 조회
    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPost(@PathVariable("postId") Long postId) {
        PostResponse response = postService.getPost(postId);
        return ResponseEntity.ok(response);
    }

    // 전체 게시글 목록 조회 (기존 GET /api/v1/posts 와 /bbs/bbslist 모두 대응)
    @GetMapping({"", "/bbslist"}) 
    public ResponseEntity<Page<PostResponse>> getAllPosts(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<PostResponse> response = postService.getAllPosts(pageable);
        return ResponseEntity.ok(response);
    }

    // 카테고리별 게시글 목록 조회
    @GetMapping("/category/{category}")
    public ResponseEntity<Page<PostResponse>> getPostsByCategory(
            @PathVariable("category") PostCategory category,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<PostResponse> response = postService.getPostsByCategory(category, pageable);
        return ResponseEntity.ok(response);
    }

    // 게시글 키워드 검색
    @GetMapping("/search")
    public ResponseEntity<Page<PostResponse>> searchPosts(
            @RequestParam("keyword") String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<PostResponse> response = postService.searchPosts(keyword, pageable);
        return ResponseEntity.ok(response);
    }

    // 게시글 수정 (관리자만 가능)
    @PutMapping(value = "/{postId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')") // 26.10.01 병합: hasAuthority('ADMIN') → hasRole('ADMIN') (세션 필터가 ROLE_ADMIN 으로 권한을 주기 때문에 기존 표기로는 관리자도 403)
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable("postId") Long postId,
            @RequestParam("currentAccountId") Long currentAccountId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin,
            @Valid @ModelAttribute PostUpdateRequest request) {
        
        PostResponse response = postService.updatePost(postId, currentAccountId, isAdmin, request);
        return ResponseEntity.ok(response);
    }

    // 게시글 삭제 (관리자만 가능)
    @DeleteMapping("/{postId}")
    @PreAuthorize("hasRole('ADMIN')") // 26.10.01 병합: hasAuthority('ADMIN') → hasRole('ADMIN') (세션 필터가 ROLE_ADMIN 으로 권한을 주기 때문에 기존 표기로는 관리자도 403)
    public ResponseEntity<Void> deletePost(
            @PathVariable("postId") Long postId,
            @RequestParam("currentAccountId") Long currentAccountId,
            @RequestParam(value = "isAdmin", defaultValue = "false") boolean isAdmin) {
        
        postService.deletePost(postId, currentAccountId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    // 추가된 첨부파일 다운로드/조회 API
    @GetMapping("/{postId}/attachments/{fileId}")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable("postId") Long postId,
            @PathVariable("fileId") Long fileId) {
        
        FileDownloadResponse fileInfo = postService.getAttachmentFile(postId, fileId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + 
                        new String(fileInfo.getOriginalFileName().getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1) + "\"")
                .body(fileInfo.getResource());
    }
}