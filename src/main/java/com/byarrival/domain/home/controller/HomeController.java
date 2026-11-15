package com.byarrival.domain.home.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.home.dto.response.HomeResponse;
import com.byarrival.domain.home.dto.response.PopularChipResponse;
import com.byarrival.domain.home.service.HomeService;
import com.byarrival.domain.user.entity.JobGroup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈")
@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "오늘의 홈 조회",
        description = "오늘 탑승 상태, 연속 이용, 스몰토크 추천, 오늘 접속자 수, 출근길 공감대 TOP3를 한 번에 돌려준다. "
            + "영역마다 따로 조회해 실패한 영역만 null이다. 서비스일은 KST 04:00에 바뀐다.")
    @GetMapping
    public ApiResponse<HomeResponse> getHome(@LoginUserId Long userId) {
        return ApiResponse.ok(homeService.getHome(userId));
    }

    @Operation(summary = "출근길 공감대 전체보기",
        description = "선택한 칩의 TOP10. jobGroup을 보내면 그 직군 칩, 빼면 전체 칩이다. 10건보다 적으면 있는 만큼만 돌려준다. "
            + "칩 범위에서 기사를 담은 사용자가 30명 미만이면 집계중 상태다.")
    @GetMapping("/popular")
    public ApiResponse<PopularChipResponse> getPopular(@RequestParam(required = false) JobGroup jobGroup) {
        return ApiResponse.ok(homeService.getPopular(jobGroup));
    }
}
