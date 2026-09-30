package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCommentUpdateRequest {

    @NotBlank(message = "댓글 내용은 필수 입력 항목입니다.")
    private String contentText;
}