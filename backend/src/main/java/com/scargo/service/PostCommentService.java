package com.scargo.service;

import com.scargo.dto.PostCommentCreateRequest;
import com.scargo.dto.PostCommentResponse;
import com.scargo.dto.PostCommentUpdateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.Post;
import com.scargo.entity.PostComment;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.PostCommentRepository;
import com.scargo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostCommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final AccountRepository accountRepository;

    // 댓글 및 대댓글 등록
    @Transactional
    public PostCommentResponse createComment(PostCommentCreateRequest request) {
        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. ID: " + request.getPostId()));

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findById(request.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + request.getAccountId()));
        }

        PostComment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("부모 댓글이 존재하지 않습니다. ID: " + request.getParentId()));

            if (!parent.getPost().getPostId().equals(post.getPostId())) {
                throw new IllegalArgumentException("부모 댓글과 게시글 정보가 일치하지 않습니다.");
            }
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .account(account)
                .parent(parent)
                .contentText(request.getContentText())
                .isDeleted(false)
                .build();

        PostComment savedComment = commentRepository.save(comment);
        return new PostCommentResponse(savedComment);
    }

    // 특정 게시글의 전체 댓글 목록 조회 (계층형 구조)
    public List<PostCommentResponse> getCommentsByPostId(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException("존재하지 않는 게시글입니다. ID: " + postId);
        }

        List<PostComment> parentComments = commentRepository.findByPostPostIdAndParentIsNullOrderByCreatedAtAsc(postId);
        return parentComments.stream()
                .map(PostCommentResponse::new)
                .toList();
    }

    // 댓글 수정
    @Transactional
    public PostCommentResponse updateComment(Long commentId, PostCommentUpdateRequest request) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다. ID: " + commentId));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new IllegalStateException("삭제된 댓글은 수정할 수 없습니다.");
        }

        // 엔티티 내부 메서드로 변경 처리
        comment.updateContent(request.getContentText());

        return new PostCommentResponse(comment);
    }

    // 댓글 삭제 (Soft Delete)
    @Transactional
    public void deleteComment(Long commentId) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다. ID: " + commentId));

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new IllegalStateException("이미 삭제된 댓글입니다.");
        }

        // 엔티티 내부 메서드로 삭제 처리
        comment.delete();
    }
}