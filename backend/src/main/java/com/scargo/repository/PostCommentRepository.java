package com.scargo.repository;

import com.scargo.entity.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    // 특정 게시글의 최상위 댓글 목록 조회 (생성일시 오름차순 정렬)
    List<PostComment> findByPostPostIdAndParentIsNullOrderByCreatedAtAsc(Long postId);
}