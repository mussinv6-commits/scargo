package com.scargo.service;

import com.scargo.dto.PostCreateRequest;
import com.scargo.dto.PostResponse;
import com.scargo.dto.PostUpdateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.Attachment;
import com.scargo.entity.Post;
import com.scargo.entity.Post.PostCategory;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.AttachmentRepository;
import com.scargo.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final AttachmentRepository attachmentRepository;
    private final AccountRepository accountsRepository;

    // 파일 저장 기본 경로
    private final String uploadDir = "C:/scargo/uploads/posts/";

    // 1. 게시글 생성 (파일 업로드 포함)
    @Transactional
    public PostResponse createPost(Long accountId, PostCreateRequest request) {
        Account account = accountsRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다. ID: " + accountId));

        Post post = Post.builder()
                .account(account)
                .title(request.getTitle())
                .contentText(request.getContentText())
                .category(request.getCategory() != null ? request.getCategory() : PostCategory.NOTICE)
                .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
                .viewCount(0)
                .build();

        Post savedPost = postRepository.save(post);

        // 첨부파일 처리
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            uploadAndSaveAttachments(savedPost, request.getFiles());
        }

        return PostResponse.from(savedPost);
    }

    // 2. 게시글 단건 조회 (조회수 증가 포함)
    @Transactional
    public PostResponse getPost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. ID: " + postId));

        post.incrementViewCount(); // 조회수 1 증가
        return PostResponse.from(post);
    }

    // 3. 전체 게시글 목록 조회 (페이징)
    public Page<PostResponse> getAllPosts(Pageable pageable) {
        return postRepository.findAll(pageable)
                .map(PostResponse::from);
    }

    // 4. 카테고리별 게시글 목록 조회 (페이징)
    public Page<PostResponse> getPostsByCategory(PostCategory category, Pageable pageable) {
        return postRepository.findByCategory(category, pageable)
                .map(PostResponse::from);
    }

    // 5. 제목/본문 키워드 검색 (페이징)
    public Page<PostResponse> searchPosts(String keyword, Pageable pageable) {
        return postRepository.findByTitleContainingOrContentTextContaining(keyword, keyword, pageable)
                .map(PostResponse::from);
    }

    // 6. 게시글 수정 (작성자/관리자 검증 + DB 첨부파일 제거 및 신규 추가, 실물 파일 삭제 안함)
    @Transactional
    public PostResponse updatePost(Long postId, Long currentAccountId, boolean isAdmin, PostUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. ID: " + postId));

        // 작성자 본인 또는 관리자 권한 검증
        if (!isAdmin && (post.getAccount() == null || !post.getAccount().getAccountId().equals(currentAccountId))) {
            throw new IllegalStateException("게시글 수정 권한이 없습니다.");
        }

        // 엔티티 정보 업데이트
        post.update(request.getTitle(), request.getContentText(), request.getCategory(), request.getIsPinned());

        // 삭제 대상 첨부파일 제거 (DB 연관관계 및 DB 레코드만 삭제)
        if (request.getDeleteFileIds() != null && !request.getDeleteFileIds().isEmpty()) {
            for (Long fileId : request.getDeleteFileIds()) {
                attachmentRepository.findById(fileId).ifPresent(attachment -> {
                    // deletePhysicalFile(attachment.getFilePath()); // 실물 파일 삭제 로직 주석 처리
                    attachmentRepository.delete(attachment);
                    post.getAttachments().remove(attachment);
                });
            }
        }

        // 신규 첨부파일 업로드
        if (request.getNewFiles() != null && !request.getNewFiles().isEmpty()) {
            uploadAndSaveAttachments(post, request.getNewFiles());
        }

        return PostResponse.from(post);
    }

    // 7. 게시글 삭제 (DB 데이터만 삭제, 실물 파일 보존)
    @Transactional
    public void deletePost(Long postId, Long currentAccountId, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 게시글입니다. ID: " + postId));

        // 작성자 본인 또는 관리자 권한 검증
        if (!isAdmin && (post.getAccount() == null || !post.getAccount().getAccountId().equals(currentAccountId))) {
            throw new IllegalStateException("게시글 삭제 권한이 없습니다.");
        }

        // 실물 파일 삭제 처리 생략 (deletePhysicalFile 호출 안함)
        postRepository.delete(post);
    }

    // --- 내부 헬퍼 메서드 ---

    // 파일 물리 저장 및 Attachment 엔티티 생성
    private void uploadAndSaveAttachments(Post post, List<MultipartFile> files) {
        File directory = new File(uploadDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;

            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String storedName = UUID.randomUUID().toString() + extension;
            String filePath = uploadDir + storedName;

            try {
                file.transferTo(new File(filePath));

                Attachment attachment = Attachment.builder()
                        .post(post)
                        .originalName(originalName)
                        .storedName(storedName)
                        .filePath(filePath)
                        .fileSize(file.getSize())
                        .fileType(file.getContentType())
                        .build();

                attachmentRepository.save(attachment);
                post.getAttachments().add(attachment);

            } catch (IOException e) {
                log.error("파일 저장 중 오류가 발생했습니다. 파일명: {}", originalName, e);
                throw new RuntimeException("파일 업로드 처리 중 오류가 발생했습니다.", e);
            }
        }
    }
}