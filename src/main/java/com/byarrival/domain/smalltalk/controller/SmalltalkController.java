package com.byarrival.domain.smalltalk.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkListResponse;
import com.byarrival.domain.smalltalk.dto.response.SmalltalkResponse;
import com.byarrival.domain.smalltalk.service.SmalltalkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Smalltalk", description = "스몰토크")
@RestController
@RequestMapping("/api/v1/smalltalks")
@RequiredArgsConstructor
public class SmalltalkController {

    private final SmalltalkService smalltalkService;

    @Operation(summary = "오늘의 스몰토크 목록",
        description = "서비스일(04:00 경계)에 만든 스몰토크. 카드: 카테고리 + 연결 뉴스 제목 / 한 줄. 비어 있으면 Empty UI.")
    @GetMapping
    public ApiResponse<SmalltalkListResponse> getToday(@LoginUserId Long userId) {
        return ApiResponse.ok(smalltalkService.getToday(userId));
    }

    @Operation(summary = "스몰토크 상세",
        description = "한 줄과 연결 뉴스(카테고리·제목·원문 주소). 삭제됐거나 연결 뉴스가 숨김·삭제되면 SMALLTALK_NOT_FOUND.")
    @GetMapping("/{smalltalkId}")
    public ApiResponse<SmalltalkResponse> get(@LoginUserId Long userId, @PathVariable Long smalltalkId) {
        return ApiResponse.ok(smalltalkService.get(userId, smalltalkId));
    }
}
