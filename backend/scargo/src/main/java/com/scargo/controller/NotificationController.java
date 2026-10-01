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
    // 26.09.21 수정: 모든 @PathVariable에 이름을 명시함.
    // (이름을 안 적으면 "-parameters" 컴파일 옵션이 없는 환경에서 Spring이 파라미터명을
    //  못 읽어 400 Bad Request가 발생함 — 실제로 이 문제로 알림 API가 전부 막혀 있었음)

    private final NotificationService notificationService;

    // 1. 알림 생성 (시스템 또는 관리자 권한 필요)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<NotificationResponse> createNotification(@RequestBody NotificationCreateRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 특정 계정의 안 읽은 알림 목록 조회 (본인 또는 관리자만 가능)
    @GetMapping("/account/{accountId}/unread")
    @PreAuthorize("hasRole('ADMIN') or #accountId == principal.id")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotificationsByAccount(@PathVariable("accountId") Long accountId) {
        List<NotificationResponse> responses = notificationService.getUnreadNotificationsByAccount(accountId);
        return ResponseEntity.ok(responses);
    }

    // 3. 특정 계정의 안 읽은 알림 개수 조회 (상단 뱃지용)
    @GetMapping("/account/{accountId}/count")
    @PreAuthorize("hasRole('ADMIN') or #accountId == principal.id")
    public ResponseEntity<Long> getUnreadCountByAccount(@PathVariable("accountId") Long accountId) {
        long count = notificationService.getUnreadCountByAccount(accountId);
        return ResponseEntity.ok(count);
    }

    // 4. 특정 계정의 전체 알림 목록 조회 (페이징 적용)
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasRole('ADMIN') or #accountId == principal.id")
    public ResponseEntity<Page<NotificationResponse>> getAllNotificationsByAccount(
            @PathVariable("accountId") Long accountId,
            @PageableDefault(size = 10) Pageable pageable) {
        Page<NotificationResponse> responses = notificationService.getNotificationsByAccountWithPaging(accountId, pageable);
        return ResponseEntity.ok(responses);
    }

    // 5. 특정 계정의 알림 유형별 필터링 조회
    @GetMapping("/account/{accountId}/filter")
    @PreAuthorize("hasRole('ADMIN') or #accountId == principal.id")
    public ResponseEntity<List<NotificationResponse>> getNotificationsByType(
            @PathVariable("accountId") Long accountId,
            @RequestParam("notificationType") NotificationType notificationType) {
        List<NotificationResponse> responses = notificationService.getNotificationsByType(accountId, notificationType);
        return ResponseEntity.ok(responses);
    }

    // 6. 알림 단건 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("notificationId") Long notificationId) {
        // TODO: 서비스 계층 또는 시큐리티에서 해당 알림의 소유자가 맞는지 추가 검증 권장
        notificationService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }

    // 7. 특정 계정의 모든 알림 '모두 읽음' 처리
    @PatchMapping("/account/{accountId}/read-all")
    @PreAuthorize("hasRole('ADMIN') or #accountId == principal.id")
    public ResponseEntity<Void> markAllAsReadByAccount(@PathVariable("accountId") Long accountId) {
        notificationService.markAllAsReadByAccount(accountId);
        return ResponseEntity.ok().build();
    }

    // 8. 알림 개별 삭제
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(@PathVariable("notificationId") Long notificationId) {
        // TODO: 소유자 검증 로직 추가 필요
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok().build();
    }

    // --- 기업(Company) 관리자용 엔드포인트 ---

    // 9. 특정 기업의 안 읽은 알림 목록 조회 
    @GetMapping("/company/{companyId}/unread")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPANY_' + #companyId)")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotificationsByCompany(@PathVariable("companyId") Long companyId) {
        List<NotificationResponse> responses = notificationService.getUnreadNotificationsByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 10. 특정 기업의 전체 알림 목록 조회 (히스토리용)
    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPANY_' + #companyId)")
    public ResponseEntity<List<NotificationResponse>> getAllNotificationsByCompany(@PathVariable("companyId") Long companyId) {
        List<NotificationResponse> responses = notificationService.getAllNotificationsByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 11. 특정 기업의 모든 안 읽은 알림 '모두 읽음' 처리
    @PatchMapping("/company/{companyId}/read-all")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPANY_' + #companyId)")
    public ResponseEntity<Void> markAllAsReadByCompany(@PathVariable("companyId") Long companyId) {
        notificationService.markAllAsReadByCompany(companyId);
        return ResponseEntity.ok().build();
    }
}