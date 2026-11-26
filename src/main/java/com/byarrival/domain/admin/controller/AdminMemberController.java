package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.AdminMemberRestrictRequest;
import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Page;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.RestrictionResult;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.service.AdminMemberRestrictionService;
import com.byarrival.domain.admin.service.AdminMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Member", description = "어드민 회원 관리")
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;
    private final AdminMemberRestrictionService restrictionService;

    @Operation(summary = "회원 목록", description = "탈퇴 회원은 제외한다. 페이지당 20개이고 page는 1부터 시작한다. 운영자만 접근한다.")
    @GetMapping
    public ApiResponse<Page> getMembers(@Valid @ModelAttribute AdminMemberSearchRequest request) {
        return ApiResponse.ok(adminMemberService.getMembers(request));
    }

    @Operation(summary = "회원 상세", description = "조회할 때마다 열람 기록을 남긴다. 운영자만 접근한다.")
    @GetMapping("/{memberId}")
    public ApiResponse<Detail> getMember(@AuthenticationPrincipal AdminPrincipal admin, @PathVariable Long memberId) {
        return ApiResponse.ok(adminMemberService.getMember(admin.id(), memberId));
    }

    @Operation(summary = "회원 이용 제한", description = "기간(DAYS_3·DAYS_7·DAYS_30·PERMANENT)과 사유(100자)는 필수다. "
        + "이미 제한 중이면 409로 거절한다. 제한된 회원은 로그인은 되지만 안내 화면 외에는 앱을 쓸 수 없다. 운영자만 접근한다.")
    @PostMapping("/{memberId}/restriction")
    public ApiResponse<RestrictionResult> restrict(@AuthenticationPrincipal AdminPrincipal admin,
        @PathVariable Long memberId, @Valid @RequestBody AdminMemberRestrictRequest request) {
        return ApiResponse.ok(restrictionService.restrict(admin.id(), memberId, request.period(), request.reason()));
    }

    @Operation(summary = "회원 이용 제한 해제", description = "적용 중인 제한을 지금 푼다. 제한 중이 아니어도 성공이다. 운영자만 접근한다.")
    @DeleteMapping("/{memberId}/restriction")
    public ApiResponse<RestrictionResult> release(@AuthenticationPrincipal AdminPrincipal admin,
        @PathVariable Long memberId) {
        return ApiResponse.ok(restrictionService.release(admin.id(), memberId));
    }
}
