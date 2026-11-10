package com.byarrival.domain.user.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.user.dto.request.NotificationPermissionRequest;
import com.byarrival.domain.user.dto.response.NotificationPermissionResponse;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.service.UserService;
import com.byarrival.domain.user.service.UserWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserWithdrawalService userWithdrawalService;

    @Operation(summary = "내 정보 조회", description = "앱 진입 시 약관 동의·온보딩 완료 여부로 화면을 분기한다.")
    @GetMapping("/me")
    public ApiResponse<UserMeResponse> getMe(@LoginUserId Long userId) {
        return ApiResponse.ok(userService.getMe(userId));
    }

    @Operation(summary = "OS 알림 권한 저장", description = "앱이 OS 알림 권한을 요청한 결과나, 폰 설정에서 바뀐 권한을 저장한다.")
    @PutMapping("/me/notification-permission")
    public ApiResponse<NotificationPermissionResponse> updateNotificationPermission(
        @LoginUserId Long userId, @Valid @RequestBody NotificationPermissionRequest request) {
        return ApiResponse.ok(userService.updateNotificationPermission(userId, request.permission()));
    }

    @Operation(summary = "회원 탈퇴",
        description = "모든 기기에서 로그아웃하고 푸시 토큰을 지운다. 프로필·설정과 이용 이력은 6개월 보관한 뒤 파기하며, "
            + "그 안에 같은 소셜 계정으로 다시 로그인하면 모두 복원된다. 탈퇴 확인은 앱 화면에서 받는다.")
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@LoginUserId Long userId) {
        userWithdrawalService.withdraw(userId);
        return ApiResponse.ok();
    }
}
