package com.scargo.dto;

import com.scargo.entity.Post.PostCategory;
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
public class PostUpdateRequest {

    @Size(max = 200, message = "제목은 최대 200자까지 입력할 수 있습니다.")
    private String title;               // 수정할 제목 (null일 경우 기존 유지)

    private String contentText;         // 수정할 본문 내용

    private PostCategory category;      // 수정할 게시판 카테고리

    private Boolean isPinned;           // 상단 고정 여부 변경

    // 기존 첨부파일 중 삭제할 파일 ID 목록
    @Builder.Default
    private List<Long> deleteFileIds = new ArrayList<>();

    // 새로 추가할 첨부파일 목록 (multipart/form-data)
    @Builder.Default
    private List<MultipartFile> newFiles = new ArrayList<>();
}