package com.scargo.repository;

import com.scargo.entity.Notification;
import com.scargo.Enum.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 1. 특정 계정의 '안 읽은' 알림 목록 조회 (최신순)
    List<Notification> findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(Long accountId);

    // 2. 특정 기업의 '안 읽은' 알림 목록 조회 (최신순)
    List<Notification> findByCompanyIdAndIsReadFalseOrderByCreatedAtDesc(Long companyId);

    // 3. 특정 계정의 '전체' 알림 목록 조회 - 히스토리용 (최신순)
    List<Notification> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    // 4. 특정 기업의 '전체' 알림 목록 조회 - 히스토리용 (최신순)
    List<Notification> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    // 5. 특정 계정의 전체 알림 페이징 조회 - 대용량 데이터 대응 (최신순)
    Page<Notification> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 6. 특정 기업의 전체 알림 페이징 조회 - 대용량 데이터 대응 (최신순)
    Page<Notification> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    // 7. 특정 계정의 특정 알림 유형별 필터링 조회 (최신순)
    List<Notification> findByAccountIdAndNotificationTypeOrderByCreatedAtDesc(Long accountId, NotificationType notificationType);

    // 8. 특정 계정의 '안 읽은' 알림 개수 조회 - 상단 뱃지 카운트용
    long countByAccountIdAndIsReadFalse(Long accountId);

    // 9. 특정 기업의 '안 읽은' 알림 개수 조회 - 기업 관리자 뱃지용
    long countByCompanyIdAndIsReadFalse(Long companyId);

    // 10. 지정된 날짜(cutoffDate) 이전에 읽은 오래된 알림 일괄 삭제 - 스케줄러용 벌크 연산
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.readAt < :cutoffDate")
    int deleteOldReadNotifications(@Param("cutoffDate") OffsetDateTime cutoffDate);
}