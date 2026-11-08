package com.byarrival.domain.term.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.term.dto.request.TermsAgreementRequest;
import com.byarrival.domain.term.dto.response.TermsAgreementResponse;
import com.byarrival.domain.term.dto.response.TermsResponse;
import com.byarrival.domain.term.service.TermService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Terms", description = "약관")
@RestController
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController {

    private final TermService termService;

    @Operation(summary = "약관 목록 조회", description = "종류별 최신 버전 약관을 조회한다.")
    @GetMapping
    public ApiResponse<List<TermsResponse>> getTerms() {
        return ApiResponse.ok(termService.getLatestTerms());
    }

    @Operation(summary = "약관 동의", description = "필수 약관에 모두 동의해야 한다. 동의 이력을 쌓는다.")
    @PostMapping("/agreements")
    public ApiResponse<List<TermsAgreementResponse>> agree(@LoginUserId Long userId,
        @Valid @RequestBody TermsAgreementRequest request) {
        return ApiResponse.ok(termService.agree(userId, request.agreements()));
    }
}
