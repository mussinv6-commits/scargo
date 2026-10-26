package com.scargo.app.service;

import com.scargo.app.dto.AppDtos.NotificationItem;
import com.scargo.entity.Notification;
import com.scargo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 26.10.03 2차: [08 알림] 1차 notifications 테이블을 그대로 사용 */
@Service
@RequiredArgsConstructor
public class AppNotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationItem> list(Long accountId, int size) {
        return notificationRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, PageRequest.of(0, Math.max(1, Math.min(size, 100))))
                .map(n -> new NotificationItem(n.getNotificationId(), n.getTitle(), n.getMessage(),
                        n.getNotificationType(), n.isRead(), n.getCreatedAt()))
                .getContent();
    }

    @Transactional
    public int readAll(Long accountId) {
        List<Notification> unread = notificationRepository.findByAccountIdAndIsReadFalseOrderByCreatedAtDesc(accountId);
        unread.forEach(Notification::markAsRead);
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}
