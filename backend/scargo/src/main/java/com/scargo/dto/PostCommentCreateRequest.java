package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCommentCreateRequest {

    @NotNull(message = "게시글 ID는 필수 입력 항목입니다.")
    private Long postId;        // 댓글을 달 게시글 ID

    private Long accountId;     // 작성자 계정 ID 

    private Long parentId;      // 부모 댓글 ID (대댓글인 경우 설정, 일반 댓글은 null)

    @NotBlank(message = "댓글 내용은 필수 입력 항목입니다.")
    private String contentText; // 댓글 내용 (필수)
}