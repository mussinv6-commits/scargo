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

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class PostCommentController {

    private final PostCommentService commentService;

    // POST /api/comments: 로그인 사용자의 댓글·대댓글 등록
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostCommentResponse> createComment(
            @Valid @RequestBody PostCommentCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(request));
    }

    // GET /api/comments/post/{postId}: 게시글의 댓글 목록 조회
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<PostCommentResponse>> getCommentsByPostId(
            @PathVariable("postId") Long postId
    ) {
        return ResponseEntity.ok(
                commentService.getCommentsByPostId(postId)
        );
    }

    // PUT /api/comments/{commentId}: 댓글 수정, Service에서 작성자 본인 확인
    @PutMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostCommentResponse> updateComment(
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody PostCommentUpdateRequest request
    ) {
        return ResponseEntity.ok(
                commentService.updateComment(commentId, request)
        );
    }

    // DELETE /api/comments/{commentId}: 댓글 삭제, Service에서 본인 또는 ADMIN 확인
    @DeleteMapping("/{commentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("commentId") Long commentId
    ) {
        commentService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }
}