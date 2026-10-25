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
import com.scargo.security.AuthenticatedAccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // 로그인 계정을 작성자로 지정하여 댓글·대댓글 등록
    @Transactional
    public PostCommentResponse createComment(
            PostCommentCreateRequest request
    ) {
        Account caller = currentAccount();
        validateContent(request.getContentText());

        // 요청으로 다른 사용자의 작성자 ID를 지정하는 행위 차단
        if (request.getAccountId() != null
                && !caller.getAccountId().equals(request.getAccountId())) {
            throw new AccessDeniedException(
                    "다른 사용자 명의로 댓글을 작성할 수 없습니다."
            );
        }

        if (request.getPostId() == null) {
            throw new IllegalArgumentException("게시글 ID가 필요합니다.");
        }

        Post post = postRepository.findById(request.getPostId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 게시글입니다."
                        )
                );

        PostComment parent = null;

        if (request.getParentId() != null) {
            parent = findComment(request.getParentId());

            if (!parent.getPost().getPostId().equals(post.getPostId())) {
                throw new IllegalArgumentException(
                        "부모 댓글과 게시글 정보가 일치하지 않습니다."
                );
            }
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .account(caller)
                .parent(parent)
                .contentText(request.getContentText())
                .isDeleted(false)
                .build();

        return new PostCommentResponse(
                commentRepository.save(comment)
        );
    }

    // 게시글의 최상위 댓글을 조회하고 기존 DTO로 대댓글 계층 구성
    public List<PostCommentResponse> getCommentsByPostId(Long postId) {
        if (postId == null) {
            throw new IllegalArgumentException("게시글 ID가 필요합니다.");
        }

        if (!postRepository.existsById(postId)) {
            throw new IllegalArgumentException(
                    "존재하지 않는 게시글입니다."
            );
        }

        return commentRepository
                .findByPostPostIdAndParentIsNullOrderByCreatedAtAsc(postId)
                .stream()
                .map(PostCommentResponse::new)
                .toList();
    }

    // 작성자 본인만 삭제되지 않은 댓글 내용 수정
    @Transactional
    public PostCommentResponse updateComment(
            Long commentId,
            PostCommentUpdateRequest request
    ) {
        Account caller = currentAccount();
        PostComment comment = findComment(commentId);

        if (!isAuthor(comment, caller)) {
            throw new AccessDeniedException(
                    "본인이 작성한 댓글만 수정할 수 있습니다."
            );
        }

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new IllegalStateException(
                    "삭제된 댓글은 수정할 수 없습니다."
            );
        }

        validateContent(request.getContentText());
        comment.updateContent(request.getContentText());

        return new PostCommentResponse(comment);
    }

    // 작성자 본인 또는 관리자만 댓글을 소프트 삭제
    @Transactional
    public void deleteComment(Long commentId) {
        Account caller = currentAccount();
        PostComment comment = findComment(commentId);

        boolean admin = caller.getUserType() == Account.UserType.ADMIN;

        if (!admin && !isAuthor(comment, caller)) {
            throw new AccessDeniedException(
                    "본인 댓글 또는 관리자 권한으로만 삭제할 수 있습니다."
            );
        }

        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new IllegalStateException(
                    "이미 삭제된 댓글입니다."
            );
        }

        comment.delete();
    }

    // 로그인 Principal의 계정 ID로 실제 계정 조회
    private Account currentAccount() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }

        AuthenticatedAccountPrincipal principal =
                (AuthenticatedAccountPrincipal) authentication.getPrincipal();

        return accountRepository.findById(principal.getId())
                .orElseThrow(() ->
                        new AccessDeniedException("유효한 계정이 아닙니다.")
                );
    }

    // 댓글 ID 검증 및 댓글 조회
    private PostComment findComment(Long commentId) {
        if (commentId == null) {
            throw new IllegalArgumentException("댓글 ID가 필요합니다.");
        }

        return commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 댓글입니다."
                        )
                );
    }

    // 탈퇴 등으로 작성자 정보가 없는 경우까지 고려하여 본인 여부 확인
    private boolean isAuthor(PostComment comment, Account caller) {
        return comment.getAccount() != null
                && caller.getAccountId().equals(
                        comment.getAccount().getAccountId()
                );
    }

    // 빈 댓글 등록·수정 차단
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
    }
}