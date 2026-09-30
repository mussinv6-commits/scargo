package com.scargo.dto;

import com.scargo.entity.Post.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostCreateRequest {

    @NotBlank(message = "제목은 필수 입력 항목입니다.")
    @Size(max = 200, message = "제목은 최대 200자까지 입력할 수 있습니다.")
    private String title; // 게시글 제목

    private String contentText; // 본문 내용

    @Builder.Default
    private PostCategory category = PostCategory.FREE; // 게시판 카테고리

    @Builder.Default
    private Boolean isPinned = false; // 상단 고정 여부

    // 첨부파일 목록 (multipart/form-data)
    @Builder.Default
    private List<MultipartFile> files = new ArrayList<>();
}