package com.byarrival.domain.user.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.user.dto.request.InterestCategoriesRequest;
import com.byarrival.domain.user.dto.request.JobGroupsRequest;
import com.byarrival.domain.user.dto.request.NotificationPermissionRequest;
import com.byarrival.domain.user.dto.request.UserUpdateRequest;
import com.byarrival.domain.user.dto.request.WeeklyCommuteRequest;
import com.byarrival.domain.user.dto.response.NotificationPermissionResponse;
import com.byarrival.domain.user.dto.response.UserMeResponse;
import com.byarrival.domain.user.service.UserService;
import com.byarrival.domain.user.service.UserSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final UserSettingService userSettingService;

    @Operation(summary = "내 정보 조회",
        description = "앱 진입 시 약관 동의·온보딩 완료 여부로 화면을 분기하고, 마이 화면에 프로필과 개인 설정을 보여 준다. "
            + "weeklyCommute에는 이동하는 요일만 월요일부터 담긴다.")
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

    @Operation(summary = "내 정보 수정",
        description = "닉네임, 이동 유형, 기본 이동시간 중 보낸 항목만 바꾼다. 다음 노선부터 반영된다. "
            + "출근 시각은 요일별 이동 설정(PUT /users/me/weekly-commute)에서 바꾼다.")
    @PatchMapping("/me")
    public ApiResponse<UserMeResponse> updateMe(@LoginUserId Long userId, @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.ok(userSettingService.updateMe(userId, request));
    }

    @Operation(summary = "관심 분야 저장", description = "보낸 목록으로 통째로 바꾼다. 활성 관심 분야 1개 이상. 다음 노선부터 반영된다.")
    @PutMapping("/me/interest-categories")
    public ApiResponse<UserMeResponse> replaceInterestCategories(
        @LoginUserId Long userId, @Valid @RequestBody InterestCategoriesRequest request) {
        return ApiResponse.ok(userSettingService.replaceInterestCategories(userId, request.categoryIds()));
    }

    @Operation(summary = "요일별 이동 설정 저장",
        description = "이동하는 요일만 시작 시각(HH:mm)과 이동시간(5~120분, 5분 단위)을 보낸다. 보내지 않은 요일은 이동하지 않는 날이 되고 "
            + "출근 알림이 가지 않는다. 하루 이상 보내야 한다.")
    @PutMapping("/me/weekly-commute")
    public ApiResponse<UserMeResponse> replaceWeeklyCommute(
        @LoginUserId Long userId, @Valid @RequestBody WeeklyCommuteRequest request) {
        return ApiResponse.ok(userSettingService.replaceWeeklyCommute(userId, request.days()));
    }

    @Operation(summary = "직군 변경",
        description = "바꾼 뒤의 직군 1~2개를 보낸다. 남는 직군은 기존 순서대로 앞에, 새로 고른 직군은 뒤에 붙고 이 순서가 홈 직군 칩 순서가 된다.")
    @PutMapping("/me/job-groups")
    public ApiResponse<UserMeResponse> replaceJobGroups(
        @LoginUserId Long userId, @Valid @RequestBody JobGroupsRequest request) {
        return ApiResponse.ok(userSettingService.replaceJobGroups(userId, request.jobGroups()));
    }
}
