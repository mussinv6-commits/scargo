package com.scargo.dto;

import com.scargo.entity.PostComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCommentResponse {

    private Long commentId;
    private Long postId;
    private Long accountId;
    private String userId; // 작성자 유저 ID (로그인 ID)
    private Long parentId;
    private String contentText;
    private Boolean isDeleted;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Builder.Default
    private List<PostCommentResponse> children = new ArrayList<>();

    // Entity -> DTO 변환 생성자
    public PostCommentResponse(PostComment comment) {
        this.commentId = comment.getCommentId();
        this.postId = comment.getPost() != null ? comment.getPost().getPostId() : null;

        // 작성자 계정 매핑 (userId 사용)
        if (comment.getAccount() != null) {
            this.accountId = comment.getAccount().getAccountId();
            this.userId = comment.getAccount().getUserId();
        } else {
            this.accountId = null;
            this.userId = "unknown"; // 또는 null / "탈퇴한 사용자"
        }

        this.parentId = comment.getParent() != null ? comment.getParent().getCommentId() : null;

        // Soft Delete 처리: 삭제된 댓글은 본문 내용을 숨김 처리
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            this.contentText = "삭제된 댓글입니다.";
        } else {
            this.contentText = comment.getContentText();
        }

        this.isDeleted = comment.getIsDeleted();
        this.deletedAt = comment.getDeletedAt();
        this.createdAt = comment.getCreatedAt();
        this.updatedAt = comment.getUpdatedAt();

        // 계층형 대댓글(children) 재귀 매핑
        if (comment.getChildren() != null && !comment.getChildren().isEmpty()) {
            this.children = comment.getChildren().stream()
                    .map(PostCommentResponse::new)
                    .toList();
        }
    }
}