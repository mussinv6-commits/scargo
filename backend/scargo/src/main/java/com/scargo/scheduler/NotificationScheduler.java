package com.scargo.scheduler;

import com.scargo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j  // 시스템 상태 추적(로그 기록) 기록용 어노테이션
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationService notificationService;

    // 매일 새벽 3시 정각에 실행되어, 지정된 일수(예: 30일) 이상 지난 '읽은 알림'을 자동 삭제합니다.
    // cron 표현식: 초 분 시 일 월 요일 (0 0 5 * * *) -> 매일 05:00:00
    
    
    @Scheduled(cron = "0 0 5 * * *")
    public void cleanUpOldNotifications() {
        log.info("=== 오래된 알림 자동 정리를 시작합니다. ===");
        
        // 30일 이전의 읽은 알림을 삭제하도록 일수(30)를 지정하여 호출합니다.
        int deletedCount = notificationService.deleteOldReadNotifications(30);
        
        log.info("=== 오래된 알림 자동 정리 완료: 총 {}건 삭제됨 ===", deletedCount);
    }
}