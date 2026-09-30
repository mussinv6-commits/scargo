package com.scargo.controller;

import com.scargo.Enum.NotificationType;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.NotificationResponse;
import com.scargo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // 1. 알림 생성 (관리자 전용)
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<NotificationResponse> createNotification(@RequestBody NotificationCreateRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 특정 계정의 안 읽은 알림 목록 조회
    @GetMapping("/account/{accountId}/unread")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotificationsByAccount(
            @PathVariable("accountId") Long accountId) {
        List<NotificationResponse> responses = notificationService.getUnreadNotificationsByAccount(accountId);
        return ResponseEntity.ok(responses);
    }

    // 3. 특정 계정의 안 읽은 알림 개수 조회 (상단 뱃지용)
    @GetMapping("/account/{accountId}/count")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<Long> getUnreadCountByAccount(
            @PathVariable("accountId") Long accountId) {
        long count = notificationService.getUnreadCountByAccount(accountId);
        return ResponseEntity.ok(count);
    }

    // 4. 특정 계정의 전체 알림 목록 조회 (페이징 적용)
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<Page<NotificationResponse>> getAllNotificationsByAccount(
            @PathVariable("accountId") Long accountId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<NotificationResponse> responses = notificationService.getNotificationsByAccountWithPaging(accountId, pageable);
        return ResponseEntity.ok(responses);
    }

    // 5. 특정 계정의 알림 유형별 필터링 조회
    @GetMapping("/account/{accountId}/filter")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByType(
            @PathVariable("accountId") Long accountId,
            @RequestParam("notificationType") NotificationType notificationType) {
        List<NotificationResponse> responses = notificationService.getNotificationsByType(accountId, notificationType);
        return ResponseEntity.ok(responses);
    }

    // 6. 알림 단건 읽음 처리 (인증된 회원)
    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<Void> markAsRead(
            @PathVariable("notificationId") Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    // 7. 특정 계정의 모든 알림 '모두 읽음' 처리
    @PatchMapping("/account/{accountId}/read-all")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<Void> markAllAsReadByAccount(
            @PathVariable("accountId") Long accountId) {
        notificationService.markAllAsReadByAccount(accountId);
        return ResponseEntity.ok().build();
    }

    // 8. 알림 개별 삭제
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED')")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable("notificationId") Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok().build();
    }

    // --- 기업(Company) 관리자용 엔드포인트 ---

    // 9. 특정 기업의 안 읽은 알림 목록 조회 
    @GetMapping("/company/{companyId}/unread")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotificationsByCompany(
            @PathVariable("companyId") Long companyId) {
        List<NotificationResponse> responses = notificationService.getUnreadNotificationsByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 10. 특정 기업의 전체 알림 목록 조회
    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<NotificationResponse>> getAllNotificationsByCompany(
            @PathVariable("companyId") Long companyId) {
        List<NotificationResponse> responses = notificationService.getAllNotificationsByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 11. 특정 기업의 모든 안 읽은 알림 '모두 읽음' 처리
    @PatchMapping("/company/{companyId}/read-all")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<Void> markAllAsReadByCompany(
            @PathVariable("companyId") Long companyId) {
        notificationService.markAllAsReadByCompany(companyId);
        return ResponseEntity.ok().build();
    }
}