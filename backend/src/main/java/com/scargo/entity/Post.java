package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId; // 게시글 고유 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", foreignKey = @ForeignKey(name = "fk_posts_account"))
    private Account account; // 작성자 ID (accounts FK)

    @Column(name = "title", nullable = false, length = 200)
    private String title; // 제목

    @Column(name = "content_text", nullable = false, columnDefinition = "TEXT")
    private String contentText; // 본문 내용

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    @Builder.Default
    private PostCategory category = PostCategory.FREE; // 게시판 카테고리 (NOTICE, FREE, QNA, FAQ)

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0; // 조회수

    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private Boolean isPinned = false; // 상단 고정 여부

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false; // 삭제 여부 (Soft Delete)

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt; // 삭제일시

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt; // 작성일시

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt; // 수정일시

    // 첨부파일 목록 연관관계 추가 (getAttachments() 자동 생성)
    @Builder.Default
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Attachment> attachments = new ArrayList<>();

    // 카테고리 Enum 정의
    public enum PostCategory {
        NOTICE,      // 공지사항
        FREE,        // 자유게시판
        QNA,         // 질문
        FAQ          // 자주 묻는 질문
    }

    // update 메서드 
    public void update(String title, String contentText, PostCategory category, Boolean isPinned) {
        if (title != null) this.title = title;
        if (contentText != null) this.contentText = contentText;
        if (category != null) this.category = category;
        if (isPinned != null) this.isPinned = isPinned;
    }

    // 비즈니스 메서드 (조회수 증가)
    public void incrementViewCount() {
        if (this.viewCount == null) {
            this.viewCount = 0;
        }
        this.viewCount++;
    }

    // 비즈니스 메서드 (Soft Delete 처리)
    public void markAsDeleted() {
        this.isDeleted = true;
        this.deletedAt = OffsetDateTime.now();
    }
}