package com.scargo.dto;

import com.scargo.entity.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentResponse {

    private Long attachmentId;   // 첨부파일 고유 ID
    private Long postId;         // 연관된 게시글 ID
    private String originalName; // 원본 파일명
    private String storedName;   // 저장된 파일명
    private String filePath;     // 파일 저장 경로/URL
    private Long fileSize;       // 파일 크기 (Byte 단위)
    private String fileType;     // MIME 타입
    private OffsetDateTime createdAt; // 생성 일시

    // Entity -> DTO 변환 메서드
    public static AttachmentResponse from(Attachment attachment) {
        if (attachment == null) {
            return null;
        }

        return AttachmentResponse.builder()
                .attachmentId(attachment.getAttachmentId())
                .postId(attachment.getPost() != null ? attachment.getPost().getPostId() : null)
                .originalName(attachment.getOriginalName())
                .storedName(attachment.getStoredName())
                .filePath(attachment.getFilePath())
                .fileSize(attachment.getFileSize())
                .fileType(attachment.getFileType())
                .createdAt(attachment.getCreatedAt())
                .build();
    }
}