package com.scargo.controller;

import com.scargo.dto.PostCommentCreateRequest;
import com.scargo.dto.PostCommentResponse;
import com.scargo.dto.PostCommentUpdateRequest;
import com.scargo.service.PostCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 게시글 댓글 및 대댓글 관리 API 컨트롤러
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService commentService;

    // 댓글 및 대댓글 등록 (로그인된 모든 사용자 가능)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<PostCommentResponse> createComment(@Valid @RequestBody PostCommentCreateRequest request) {
        PostCommentResponse response = commentService.createComment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 특정 게시글의 전체 댓글 목록 조회 (SecurityConfig에서 permitAll() 또는 로그인 회원 조회)
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<PostCommentResponse>> getCommentsByPostId(@PathVariable("postId") Long postId) {
        List<PostCommentResponse> comments = commentService.getCommentsByPostId(postId);
        return ResponseEntity.ok(comments);
    }

    // 댓글 수정 (로그인된 모든 사용자 가능)
    @PutMapping("/{commentId}")
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<PostCommentResponse> updateComment(
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody PostCommentUpdateRequest request) {
        PostCommentResponse response = commentService.updateComment(commentId, request);
        return ResponseEntity.ok(response);
    }

    // 댓글 삭제 (로그인된 모든 사용자 가능)
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Void> deleteComment(@PathVariable("commentId") Long commentId) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}