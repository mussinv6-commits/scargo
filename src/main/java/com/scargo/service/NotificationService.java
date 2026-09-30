package com.scargo.service;

import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.NotificationResponse;
import com.scargo.entity.Notification;
import com.scargo.Enum.NotificationType;
import com.scargo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

 // 1. 알림 생성 (발송)
    @Transactional
    public NotificationResponse createNotification(NotificationCreateRequest request) {
        Notification notification = Notification.builder()
                .accountId(request.getAccountId())
                .companyId(request.getCompanyId())
                .title(request.getTitle())
                .message(request.getMessage())
                .notificationType(request.getNotificationType().name()) // [수정] .name()을 붙여서 String으로 변환하여 대입
                .referenceId(request.getReferenceId())
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        return NotificationResponse.from(savedNotification);
    }

    // 2. 특정 계정의 안 읽은 알림 목록 조회
    public List<NotificationResponse> getUnreadNotificationsByAccount(Long accountId) {
        List<Notification> notifications = notificationRepository.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 3. 알림 읽음 처리
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다. ID: " + notificationId));
        
        notification.markAsRead();
    }

    // 4. 알림 개별 삭제
    @Transactional
    public void deleteNotification(Long notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new IllegalArgumentException("존재하지 않는 알림입니다. ID: " + notificationId);
        }
        notificationRepository.deleteById(notificationId);
    }
    
    // 5. 특정 계정의 '전체' 알림 목록 조회 (히스토리용)
    public List<NotificationResponse> getAllNotificationsByAccount(Long accountId) {
        List<Notification> notifications = notificationRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 5-1.특정 계정의 알림 페이징 조회 (대용량 대응)
    public Page<NotificationResponse> getNotificationsByAccountWithPaging(Long accountId, Pageable pageable) {
        return notificationRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(NotificationResponse::from);
    }

    // 6. 특정 기업의 안 읽은 알림 목록 조회
    public List<NotificationResponse> getUnreadNotificationsByCompany(Long companyId) {
        List<Notification> notifications = notificationRepository.findByCompanyIdAndIsReadFalseOrderByCreatedAtDesc(companyId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 7. 특정 계정의 모든 알림 '모두 읽음' 처리
    @Transactional
    public void markAllAsReadByAccount(Long accountId) {
        List<Notification> unreadNotifications = notificationRepository.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId);
        for (Notification notification : unreadNotifications) {
            notification.markAsRead();
        }
    }

    // 7-1. 특정 기업의 모든 안 읽은 알림 '모두 읽음' 처리
    @Transactional
    public void markAllAsReadByCompany(Long companyId) {
        List<Notification> unreadNotifications = notificationRepository.findByCompanyIdAndIsReadFalseOrderByCreatedAtDesc(companyId);
        for (Notification notification : unreadNotifications) {
            notification.markAsRead();
        }
    }
    
    // 8. 특정 계정의 안 읽은 알림 개수 조회
    public long getUnreadCountByAccount(Long accountId) {
        return notificationRepository.countByAccountIdAndIsReadFalse(accountId);
    }

    // 9. 특정 기업의 '전체' 알림 목록 조회 (히스토리용)
    public List<NotificationResponse> getAllNotificationsByCompany(Long companyId) {
        List<Notification> notifications = notificationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 10.특정 계정의 알림 유형별 필터링 조회 (예: 과적 경고, 기업 승인 등)
    public List<NotificationResponse> getNotificationsByType(Long accountId, NotificationType notificationType) {
        List<Notification> notifications = notificationRepository.findByAccountIdAndNotificationTypeOrderByCreatedAtDesc(accountId, notificationType);
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 11. 지정된 일수(daysAgo)보다 오래된 '읽은' 알림 일괄 삭제 (스케줄러용)
    @Transactional
    public int deleteOldReadNotifications(int daysAgo) {
        OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(daysAgo);
        return notificationRepository.deleteOldReadNotifications(cutoffDate);
    }
}