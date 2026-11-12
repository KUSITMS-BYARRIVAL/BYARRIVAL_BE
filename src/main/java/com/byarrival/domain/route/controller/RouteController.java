package com.byarrival.domain.route.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.common.security.LoginUserId;
import com.byarrival.domain.route.dto.request.RouteCreateRequest;
import com.byarrival.domain.route.dto.request.RouteCursorRequest;
import com.byarrival.domain.route.dto.response.ArrivalResponse;
import com.byarrival.domain.route.dto.response.BoardingPassResponse;
import com.byarrival.domain.route.dto.response.RideResponse;
import com.byarrival.domain.route.dto.response.RouteResponse;
import com.byarrival.domain.route.dto.response.RouteStopResponse;
import com.byarrival.domain.route.dto.response.StopReadResponse;
import com.byarrival.domain.route.dto.response.TodayRouteResponse;
import com.byarrival.domain.route.service.RouteRideService;
import com.byarrival.domain.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Route", description = "오늘의 노선 생성·후보 카드 담기·시작하기")
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteRideService routeRideService;

    @Operation(summary = "오늘 탑승 상태 조회",
        description = "탑승 CTA와 앱 재진입 시 화면을 고른다. NONE이면 컨디션 선택, CREATED면 카드 선택 화면(담은 카드·마지막 위치 복원), "
            + "BOARDING이면 보딩패스, IN_PROGRESS면 운행 화면, COMPLETED면 결과 화면. 서비스일은 KST 04:00에 바뀐다.")
    @GetMapping("/today")
    public ApiResponse<TodayRouteResponse> getToday(@LoginUserId Long userId) {
        return ApiResponse.ok(routeService.getToday(userId));
    }

    @Operation(summary = "노선 생성",
        description = "컨디션을 받아 후보 카드 풀을 만든다. 오늘 이미 만든 노선이 있으면 새로 만들지 않고 그 노선을 돌려준다(컨디션 변경 불가). "
            + "추천 기사가 0건이면 노선 없이 EMPTY('오늘 준비한 뉴스는 여기까지에요')를 돌려준다.")
    @PostMapping
    public ApiResponse<TodayRouteResponse> create(
        @LoginUserId Long userId, @Valid @RequestBody RouteCreateRequest request) {
        return ApiResponse.ok(routeService.create(userId, request.condition()));
    }

    @Operation(summary = "후보 카드 담기",
        description = "담은 순서가 운행 순서가 된다. 이미 담은 카드면 그대로 둔다. 담으면 최대 시간(T×120%)을 넘는 카드는 ROUTE_CART_OVER_LIMIT.")
    @PutMapping("/{routeId}/cart/{articleId}")
    public ApiResponse<RouteResponse> addCard(
        @LoginUserId Long userId, @PathVariable Long routeId, @PathVariable Long articleId) {
        return ApiResponse.ok(routeService.addCard(userId, routeId, articleId));
    }

    @Operation(summary = "후보 카드 빼기", description = "담지 않은 카드면 그대로 둔다.")
    @DeleteMapping("/{routeId}/cart/{articleId}")
    public ApiResponse<RouteResponse> removeCard(
        @LoginUserId Long userId, @PathVariable Long routeId, @PathVariable Long articleId) {
        return ApiResponse.ok(routeService.removeCard(userId, routeId, articleId));
    }

    @Operation(summary = "마지막으로 본 카드 위치 저장",
        description = "앱 재진입 시 카드 위치를 복원하는 데 쓴다. 스와이프마다 보내지 말고 화면을 떠날 때나 일정 간격으로 보낸다.")
    @PutMapping("/{routeId}/cursor")
    public ApiResponse<Void> saveCursor(
        @LoginUserId Long userId, @PathVariable Long routeId, @Valid @RequestBody RouteCursorRequest request) {
        routeService.saveCursor(userId, routeId, request.cardOrder());
        return ApiResponse.ok();
    }

    @Operation(summary = "시작하기 (보딩패스)",
        description = "담은 카드 합계가 T×70~120%이거나 더 담을 카드가 없으면 보딩패스 단계로 넘기고 보딩패스를 돌려준다. "
            + "이미 보딩패스 단계면 같은 보딩패스를 돌려준다. 조건을 만족하지 않으면 ROUTE_NOT_STARTABLE.")
    @PostMapping("/{routeId}/boarding")
    public ApiResponse<BoardingPassResponse> board(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeService.board(userId, routeId));
    }

    @Operation(summary = "출발하기",
        description = "보딩패스 단계의 노선을 담은 카드·순서로 확정하고 운행을 시작한다. 이미 운행 중이면 그대로 돌려준다. "
            + "운행 화면은 progress.currentStop번 정거장부터 연다.")
    @PostMapping("/{routeId}/departure")
    public ApiResponse<RideResponse> depart(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeRideService.depart(userId, routeId));
    }

    @Operation(summary = "정거장 기사 조회",
        description = "n번째 정거장(담은 순서, 1부터) 기사와 진행 상태. 운행 중이거나 오늘 완주한 노선만 볼 수 있다. "
            + "available이 false면 숨겼거나 원문이 삭제된 기사라 건너뛴다.")
    @GetMapping("/{routeId}/stops/{stopNumber}")
    public ApiResponse<RouteStopResponse> getStop(
        @LoginUserId Long userId, @PathVariable Long routeId, @PathVariable int stopNumber) {
        return ApiResponse.ok(routeRideService.getStop(userId, routeId, stopNumber));
    }

    @Operation(summary = "기사 보러가기 (완독)",
        description = "[기사 보러가기]를 누른 순간 완독으로 기록한다. 원문 열기에 실패해도 완독이다. 이미 읽은 기사면 그대로 둔다.")
    @PostMapping("/{routeId}/stops/{stopNumber}/read")
    public ApiResponse<StopReadResponse> read(
        @LoginUserId Long userId, @PathVariable Long routeId, @PathVariable int stopNumber) {
        return ApiResponse.ok(routeRideService.read(userId, routeId, stopNumber));
    }

    @Operation(summary = "도착하기 (완주)",
        description = "남은 기사가 없으면 완주로 기록하고 완주 결과를 돌려준다. 이미 완주했으면 같은 결과를 돌려준다. "
            + "읽지 않은 기사가 있으면 ROUTE_NOT_ARRIVABLE, 만료된 노선은 ROUTE_EXPIRED.")
    @PostMapping("/{routeId}/arrival")
    public ApiResponse<ArrivalResponse> arrive(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeRideService.arrive(userId, routeId));
    }

    @Operation(summary = "보딩패스에서 뒤로 가기", description = "담은 카드를 유지한 채 카드 선택 단계로 돌아간다.")
    @DeleteMapping("/{routeId}/boarding")
    public ApiResponse<RouteResponse> returnToCardSelection(@LoginUserId Long userId, @PathVariable Long routeId) {
        return ApiResponse.ok(routeService.returnToCardSelection(userId, routeId));
    }
}
