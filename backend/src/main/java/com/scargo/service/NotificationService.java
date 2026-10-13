package com.scargo.service;

import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.NotificationResponse;
import com.scargo.entity.Notification;
import com.scargo.Enum.NotificationType;
import com.scargo.repository.NotificationRepository;
// [FCM 비활성화 26.09.30] import com.scargo.service.FcmPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation; // 26.10.01 추가: 미등록 차량 알림용
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;
    // [FCM 비활성화 26.09.30] private final FcmPushService fcmPushService; // 26.09.22 추가: 인앱 알림 생성 시 모바일 푸시도 함께 발송

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

        // [FCM 비활성화 26.09.30] Firebase 분리로 푸시 발송 주석 처리 (firebase_separated 폴더 참고)
        // // 26.09.22 추가: DB 저장(인앱 알림)과 별개로, 계정에 등록된 기기가 있으면 FCM 푸시도 보낸다.
        // // companyId 단위 알림(개별 accountId 없음)은 현재 범위에서 제외 - 필요 시 회사 소속 계정 전체 조회 후 반복 발송하도록 확장 가능.
        // if (savedNotification.getAccountId() != null) {
        //     fcmPushService.sendToAccount(
        //             savedNotification.getAccountId(),
        //             savedNotification.getTitle(),
        //             savedNotification.getMessage(),
        //             savedNotification.getNotificationType(),
        //             savedNotification.getReferenceId()
        //     );
        // }

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

    // 26.09.30 병합: 백엔드.zip 의 타이어 알림 기능
    // 12. 타이어 10만 km 단위 도달 알림 발송 메서드
    @Transactional
    public void checkAndNotifyTireMilestone(Long accountId, Long companyId, String vehicleNo, String tirePosition, int currentMileage) {
        // 현재 주행거리가 10만 km 단위인지 확인 (예: 100000, 200000...)
        if (currentMileage > 0 && currentMileage % 100000 == 0) {
            String title = "🛞 타이어 점검/교체 주기 도달 알림";
            String message = String.format("차량 [%s] (%s)의 누적 주행거리가 %d km에 도달했습니다. 타이어 점검 및 교체를 확인하세요.", 
                                           vehicleNo, tirePosition, currentMileage);

            NotificationCreateRequest request = NotificationCreateRequest.builder()
                    .accountId(accountId)
                    .companyId(companyId)
                    .title(title)
                    .message(message)
                    .notificationType(NotificationType.TIRE_ALERT) // NotificationType에 TIRE_ALERT 상수가 있다고 가정
                    .referenceId(null) // 필요시 타이어 ID나 차량 ID 매핑
                    .build();

            createNotification(request);
        }
    }

    // 26.10.01 추가(미등록 차량 알림): 호출한 쪽 트랜잭션과 분리해서 저장
    // (알림 저장이 실패해도 게이트 기록 저장 등 호출한 쪽 작업은 롤백되지 않음)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationResponse createNotificationInNewTx(NotificationCreateRequest request) {
        return createNotification(request);
    }
    
    // 관리자 계정 ID 전체 (별도 트랜잭션: 조회가 실패해도 호출한 쪽 작업에 영향 없음)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findAdminAccountIds() {
        return jdbcTemplate.queryForList(
                "SELECT account_id FROM accounts WHERE user_type = 'ADMIN'", Long.class);
    }
    // 해당 기업 소속 승인된 기업회원 계정 ID 전체
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findCompanyAccountIds(Long companyId) {
        return jdbcTemplate.queryForList(
                "SELECT account_id FROM accounts WHERE company_id = ? AND user_type = 'CORPORATE_APPROVED'",
                Long.class, companyId);
    }
    
}