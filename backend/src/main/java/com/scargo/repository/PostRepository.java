package com.scargo.repository;

import com.scargo.entity.Post;
import com.scargo.entity.Post.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // 1. 카테고리별 게시글 목록 조회 (페이징)
    Page<Post> findByCategory(PostCategory category, Pageable pageable);

    // 2. 제목 또는 본문에 키워드가 포함된 게시글 검색 (페이징)
    Page<Post> findByTitleContainingOrContentTextContaining(String titleKeyword, String contentKeyword, Pageable pageable);

    // 3. 상단 고정(isPinned = true) 게시글 목록 조회
    Page<Post> findByIsPinnedTrue(Pageable pageable);

    // 4. 작성자(Accounts) ID 기준 게시글 목록 조회 (페이징)
    Page<Post> findByAccountAccountId(Long accountId, Pageable pageable);
}