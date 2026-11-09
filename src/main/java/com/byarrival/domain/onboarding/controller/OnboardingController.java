package com.byarrival.domain.onboarding.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.onboarding.dto.request.OnboardingUpdateRequest;
import com.byarrival.domain.onboarding.dto.response.OnboardingResponse;
import com.byarrival.domain.onboarding.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Onboarding", description = "온보딩")
@RestController
@RequestMapping("/api/v1/users/me/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @Operation(summary = "온보딩 정보 조회", description = "지금까지 저장한 값을 돌려준다. 중간에 이탈했다가 돌아오면 이 값으로 이어서 진행한다.")
    @GetMapping
    public ApiResponse<OnboardingResponse> getOnboarding(@LoginUserId Long userId) {
        return ApiResponse.ok(onboardingService.getOnboarding(userId));
    }

    @Operation(summary = "온보딩 정보 저장",
        description = "화면마다 입력한 값만 보낸다. 보내지 않은 항목은 바꾸지 않는다. "
            + "categoryIds를 보내면 그 목록으로 통째로 바꾼다. commuteStartTime은 HH:mm 형식이다.")
    @PatchMapping
    public ApiResponse<OnboardingResponse> updateOnboarding(
        @LoginUserId Long userId, @Valid @RequestBody OnboardingUpdateRequest request) {
        return ApiResponse.ok(onboardingService.updateOnboarding(userId, request));
    }

    @Operation(summary = "온보딩 완료",
        description = "이동 유형, 이동 시작 시각, 기본 이동시간, 관심 분야 1개 이상이 저장돼 있으면 완료한다. 이미 완료했으면 그대로 성공한다.")
    @PostMapping("/complete")
    public ApiResponse<OnboardingResponse> completeOnboarding(@LoginUserId Long userId) {
        return ApiResponse.ok(onboardingService.completeOnboarding(userId));
    }
}
