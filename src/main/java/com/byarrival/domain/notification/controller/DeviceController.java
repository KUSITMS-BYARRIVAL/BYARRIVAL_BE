package com.byarrival.domain.notification.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.notification.dto.request.DeviceRegisterRequest;
import com.byarrival.domain.notification.dto.request.DeviceUnregisterRequest;
import com.byarrival.domain.notification.service.DeviceTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Device", description = "푸시 기기 토큰")
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceTokenService deviceTokenService;

    @Operation(summary = "기기 토큰 등록·갱신",
        description = "로그인 직후, 앱 실행 시, FCM 토큰이 바뀔 때 호출한다. 같은 토큰을 다시 보내도 된다.")
    @PutMapping
    public ApiResponse<Void> register(@LoginUserId Long userId, @Valid @RequestBody DeviceRegisterRequest request) {
        deviceTokenService.register(userId, request.fcmToken(), request.platform());
        return ApiResponse.ok();
    }

    @Operation(summary = "기기 토큰 삭제", description = "이 기기로 더 이상 푸시를 받지 않는다. 없는 토큰이어도 성공이다.")
    @DeleteMapping
    public ApiResponse<Void> unregister(@LoginUserId Long userId, @Valid @RequestBody DeviceUnregisterRequest request) {
        deviceTokenService.unregister(userId, request.fcmToken());
        return ApiResponse.ok();
    }
}
