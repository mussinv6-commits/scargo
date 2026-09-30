package com.scargo.dto;

import com.scargo.entity.Attachment;
import com.scargo.entity.Post;
import com.scargo.entity.Post.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// 게시글 상세 응답 DTO
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponse {

    private Long postId;               // 게시글 고유 ID
    private Long accountId;            // 작성자 계정 ID
    private String title;              // 제목
    private String contentText;        // 본문 내용
    private PostCategory category;     // 게시판 카테고리
    private Integer viewCount;         // 조회수
    private Boolean isPinned;          // 상단 고정 여부
    private OffsetDateTime createdAt;  // 작성일시
    private OffsetDateTime updatedAt;  // 수정일시

    // 첨부파일 응답 목록
    private List<AttachmentResponse> attachments;

    // Entity -> DTO 변환 정적 팩토리 메서드
    public static PostResponse from(Post post) {
        return PostResponse.builder()
                .postId(post.getPostId())
                .accountId(post.getAccount() != null ? post.getAccount().getAccountId() : null)
                .title(post.getTitle())
                .contentText(post.getContentText())
                .category(post.getCategory())
                .viewCount(post.getViewCount())
                .isPinned(post.getIsPinned())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .attachments(Optional.ofNullable(post.getAttachments())
                        .orElseGet(Collections::emptyList)
                        .stream()
                        .map(AttachmentResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }

    // 첨부파일 내포 DTO
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttachmentResponse {
        private Long attachmentId;     // 첨부파일 ID
        private String originalName;   // 원본 파일명
        private String storedName;     // 저장된 파일명
        private String filePath;       // 저장 경로/URL
        private Long fileSize;         // 파일 크기 (Byte)
        private String fileType;       // 파일 타입

        public static AttachmentResponse from(Attachment attachment) {
            return AttachmentResponse.builder()
                    .attachmentId(attachment.getAttachmentId())
                    .originalName(attachment.getOriginalName())
                    .storedName(attachment.getStoredName())
                    .filePath(attachment.getFilePath())
                    .fileSize(attachment.getFileSize())
                    .fileType(attachment.getFileType())
                    .build();
        }
    }
}