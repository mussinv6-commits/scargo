package com.scargo.service;

import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.NotificationResponse;
import com.scargo.entity.Notification;
import com.scargo.Enum.NotificationType;
import com.scargo.repository.NotificationRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
// [FCM 비활성화 26.09.30] import com.scargo.service.FcmPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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
        List<Notification> notifications = notificationRepository.findMineUnread(accountId, carrierCompany(accountId)); // fix_1530
        return notifications.stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    // 3. 알림 읽음 처리
    // 26.10.22 수정: 알림 소유자(또는 관리자)만 처리 가능
    @Transactional
    public void markAsRead(Long notificationId, AuthenticatedAccountPrincipal caller) {
        Notification notification = findOwnedNotification(notificationId, caller);
        notification.markAsRead();
    }

    // 4. 알림 개별 삭제
    // 26.10.22 수정: 알림 소유자(또는 관리자)만 삭제 가능
    @Transactional
    public void deleteNotification(Long notificationId, AuthenticatedAccountPrincipal caller) {
        Notification notification = findOwnedNotification(notificationId, caller);
        notificationRepository.delete(notification);
    }

    // 26.10.22 추가: 알림 소유자 확인
    //  - 계정 알림: 수신 계정 본인
    //  - 업체 알림(수신 계정 없음): 같은 업체 소속 계정 (업체 알림 조회 API의 COMPANY_<id> 권한과 같은 기준)
    //  - 관리자: 전체
    private Notification findOwnedNotification(Long notificationId, AuthenticatedAccountPrincipal caller) {
        if (caller == null) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 알림입니다. ID: " + notificationId));

        boolean admin = "ADMIN".equals(caller.getUserType());
        boolean accountOwner = notification.getAccountId() != null
                && notification.getAccountId().equals(caller.getId());
        boolean companyOwner = notification.getAccountId() == null
                && notification.getCompanyId() != null
                && notification.getCompanyId().equals(caller.getCompanyId());
        if (!admin && !accountOwner && !companyOwner) {
            throw new AccessDeniedException("본인 또는 소속 업체의 알림만 처리할 수 있습니다.");
        }
        return notification;
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
        return notificationRepository.findMine(accountId, carrierCompany(accountId), pageable) // fix_1530: 운송사 담당자는 회사 알림 포함
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
        List<Notification> unreadNotifications = notificationRepository.findMineUnread(accountId, carrierCompany(accountId)); // fix_1530
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
        return notificationRepository.countMineUnread(accountId, carrierCompany(accountId)); // fix_1530
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
        List<Notification> notifications = notificationRepository.findByAccountIdAndNotificationTypeOrderByCreatedAtDesc(accountId, notificationType.name());
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
                // 26.10.20: 승인 운송사의 담당자 (user_type CORPORATE_APPROVED, 승인 상태는 companies.approval_status)
                "SELECT a.account_id FROM accounts a JOIN companies c ON c.company_id = a.company_id "
              + "WHERE a.company_id = ? AND a.user_type = 'CORPORATE_APPROVED' AND c.is_carrier = TRUE AND c.approval_status = 'APPROVED'",
                Long.class, companyId);
    }

    /** fix_1530: 승인된 운송사 담당자(CORPORATE_APPROVED + is_carrier + APPROVED)면 회사 ID, 아니면 null.
     *  회사 알림(company_id)은 담당자 계정이 아니라 회사에 저장되므로 웹 알림 종에서 함께 보여 준다 (읽음 표시는 회사 담당자끼리 공유) */
    private Long carrierCompany(Long accountId) {
        return jdbcTemplate.query("""
            SELECT a.company_id FROM accounts a JOIN companies c ON c.company_id = a.company_id
             WHERE a.account_id = ? AND a.user_type = 'CORPORATE_APPROVED' AND c.is_carrier AND c.approval_status = 'APPROVED'""",
            rs -> rs.next() ? rs.getLong(1) : null, accountId);
    }
}
