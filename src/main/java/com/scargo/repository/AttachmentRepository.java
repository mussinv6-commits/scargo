package com.scargo.repository;

import com.scargo.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    // 특정 게시글(postId)에 속한 첨부파일 목록 조회
    List<Attachment> findByPost_PostId(Long postId);

    // 특정 게시글(postId)에 속한 모든 첨부파일 삭제
    void deleteByPost_PostId(Long postId);
}