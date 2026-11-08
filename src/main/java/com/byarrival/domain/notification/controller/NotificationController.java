package com.byarrival.domain.notification.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.notification.dto.response.NotificationPageResponse;
import com.byarrival.domain.notification.dto.response.TestPushResponse;
import com.byarrival.domain.notification.dto.response.UnreadCountResponse;
import com.byarrival.domain.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification", description = "알림함·테스트 발송")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "알림함 목록",
        description = "최근 30일 알림을 최신순으로 돌려준다. 첫 요청은 cursor 없이, 다음부터 nextCursor를 넣는다. size는 기본 20, 최대 50.")
    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
        @LoginUserId Long userId,
        @RequestParam(required = false) Long cursor,
        @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(notificationService.getNotifications(userId, cursor, size));
    }

    @Operation(summary = "안 읽은 알림 개수")
    @GetMapping("/unread-count")
    public ApiResponse<UnreadCountResponse> getUnreadCount(@LoginUserId Long userId) {
        return ApiResponse.ok(notificationService.getUnreadCount(userId));
    }

    @Operation(summary = "알림 읽음", description = "이미 읽은 알림이어도 성공이다.")
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@LoginUserId Long userId, @PathVariable Long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ApiResponse.ok();
    }

    @Operation(summary = "알림 모두 읽음")
    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@LoginUserId Long userId) {
        notificationService.markAllRead(userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "테스트 푸시 발송",
        description = "내 계정에 등록된 모든 기기로 테스트 푸시를 보내고 알림함에도 저장한다. 수신 설정과 상관없이 보낸다. 개발·QA용.")
    @PostMapping("/test")
    public ApiResponse<TestPushResponse> sendTest(@LoginUserId Long userId) {
        return ApiResponse.ok(notificationService.sendTest(userId));
    }
}
