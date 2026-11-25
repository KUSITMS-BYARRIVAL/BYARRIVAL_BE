package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.AdminMemberSearchRequest;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Detail;
import com.byarrival.domain.admin.dto.response.AdminMemberResponses.Page;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.service.AdminMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Member", description = "어드민 회원 관리")
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

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
}
