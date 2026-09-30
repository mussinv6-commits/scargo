package com.scargo.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "post_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private PostComment parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<PostComment> children = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contentText;

    private Boolean isDeleted;
    private OffsetDateTime deletedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Builder
    public PostComment(Post post, Account account, PostComment parent, String contentText, Boolean isDeleted) {
        this.post = post;
        this.account = account;
        this.parent = parent;
        this.contentText = contentText;
        this.isDeleted = isDeleted != null ? isDeleted : false;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    // 도메인 비즈니스 메서드
    public void updateContent(String contentText) {
        this.contentText = contentText;
        this.updatedAt = OffsetDateTime.now();
    }

    public void delete() {
        this.isDeleted = true;
        this.deletedAt = OffsetDateTime.now();
    }
}