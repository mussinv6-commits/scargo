package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NotificationUpdateRequest {

    private boolean isRead; // 알림 읽음 상태 변경 (true/false)
}