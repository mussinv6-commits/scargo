package com.scargo.dto;

import com.scargo.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long notificationId;
    private Long accountId;
    private Long companyId;
    private String title;
    private String message;
    private String notificationType;
    private Long referenceId;
    private boolean isRead;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;

    // Entity 객체를 DTO로 변환
    public static NotificationResponse from(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .accountId(notification.getAccountId())
                .companyId(notification.getCompanyId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .notificationType(notification.getNotificationType())
                .referenceId(notification.getReferenceId())
                .isRead(notification.isRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}