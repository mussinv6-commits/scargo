package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId; // 첨부파일 고유 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post; // 연관된 게시글 (FK)

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName; // 원본 파일명

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName; // 저장된 파일명

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath; // 파일 저장 경로/URL

    @Column(name = "file_size", nullable = false)
    private Long fileSize; // 파일 크기 (Byte 단위)

    @Column(name = "file_type", length = 100)
    private String fileType; // MIME 타입

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt; // 생성 일시

    // 게시글과의 연관관계 편의 메서드
    public void setPost(Post post) {
        this.post = post;
    }
}