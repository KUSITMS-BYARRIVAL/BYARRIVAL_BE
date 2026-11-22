package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.AdminLoginRequest;
import com.byarrival.domain.admin.dto.response.AdminResponse;
import com.byarrival.domain.admin.security.AdminCookieFactory;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.admin.service.AdminAuthService;
import com.byarrival.domain.admin.service.AdminAuthService.LoginResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Auth", description = "어드민 로그인")
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminCookieFactory cookieFactory;

    @Operation(summary = "어드민 로그인",
        description = "성공하면 HttpOnly 쿠키로 토큰을 준다. 웹은 요청에 credentials: include를 넣는다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        LoginResult result = adminAuthService.login(request.email(), request.password());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieFactory.create(result.token()).toString())
            .body(ApiResponse.ok(AdminResponse.from(result.admin())));
    }

    @Operation(summary = "로그인한 어드민 정보", description = "새로고침 때 로그인 상태를 확인한다. 만료됐으면 401이다.")
    @GetMapping("/me")
    public ApiResponse<AdminResponse> me(@AuthenticationPrincipal AdminPrincipal admin) {
        return ApiResponse.ok(AdminResponse.from(admin));
    }

    @Operation(summary = "어드민 로그아웃", description = "쿠키를 지우고 이 계정에 발급한 토큰을 모두 무효로 만든다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AdminPrincipal admin) {
        adminAuthService.logout(admin.id());
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookieFactory.clear().toString())
            .body(ApiResponse.ok());
    }
}
