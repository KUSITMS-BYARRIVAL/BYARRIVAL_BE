package com.byarrival.common.controller;

import com.byarrival.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬에서 Swagger와 공통 응답 형식을 확인하는 테스트 API (local 프로필 전용)
 */
@Tag(name = "Test", description = "로컬 확인용 테스트 API")
@Profile("local")
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Operation(summary = "성공 응답 확인")
    @GetMapping
    public ApiResponse<String> success() {
        return ApiResponse.ok("ok");
    }
}
