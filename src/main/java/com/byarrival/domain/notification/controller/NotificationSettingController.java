package com.byarrival.domain.notification.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.notification.dto.request.NotificationSettingUpdateRequest;
import com.byarrival.domain.notification.dto.response.NotificationSettingsResponse;
import com.byarrival.domain.notification.service.NotificationSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notification Setting", description = "알림 수신 설정")
@RestController
@RequestMapping("/api/v1/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @Operation(summary = "수신 설정 조회",
        description = "카테고리별 수신 여부와 OS 알림 권한을 돌려준다. 지금 카테고리는 ACTIVITY(운영 안내를 포함한 전체 알림) 하나다. "
            + "바꾼 적 없으면 켜져 있다. 꺼도 알림함에는 쌓인다.")
    @GetMapping
    public ApiResponse<NotificationSettingsResponse> getSettings(@LoginUserId Long userId) {
        return ApiResponse.ok(notificationSettingService.getSettings(userId));
    }

    @Operation(summary = "수신 설정 변경", description = "바꿀 카테고리만 보낸다. 변경 후 전체 설정을 돌려준다.")
    @PatchMapping
    public ApiResponse<NotificationSettingsResponse> updateSettings(
        @LoginUserId Long userId, @Valid @RequestBody NotificationSettingUpdateRequest request) {
        return ApiResponse.ok(notificationSettingService.updateSettings(userId, request));
    }
}
