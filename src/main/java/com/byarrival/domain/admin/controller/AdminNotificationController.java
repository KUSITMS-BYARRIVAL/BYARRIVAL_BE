package com.byarrival.domain.admin.controller;

import com.byarrival.common.response.ApiResponse;
import com.byarrival.domain.admin.dto.request.AdminNotificationRequest;
import com.byarrival.domain.admin.dto.request.AdminNotificationTestRequest;
import com.byarrival.domain.admin.security.AdminPrincipal;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.Broadcast;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.Estimate;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.History;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.TestSend;
import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import com.byarrival.domain.notification.service.BroadcastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Notification", description = "어드민 알림 발송")
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final BroadcastService broadcastService;

    @Operation(summary = "예상 인원", description = "보내기 전 확인 Alert의 인원. 알림 수신 설정을 끈 사람과 OS 알림 권한을 "
        + "거부한 사람은 빠진다. 이벤트는 광고성 정보 수신 동의자만, 21~08시 발송이면 야간 광고 동의자만 센다.")
    @GetMapping("/estimate")
    public ApiResponse<Estimate> estimate(@RequestParam BroadcastKind kind, @RequestParam BroadcastTarget target,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledAt) {
        return ApiResponse.ok(broadcastService.estimate(kind, target, scheduledAt));
    }

    @Operation(summary = "알림 발송·예약", description = "scheduledAt이 없으면 즉시 보낸다. 예약 시각은 분 단위이고 지금보다 "
        + "뒤여야 한다. 받을 사람이 0명이면 409. 공지사항(NOTICE) 화면은 https url이 필요하다.")
    @PostMapping
    public ApiResponse<Broadcast> create(@AuthenticationPrincipal AdminPrincipal admin,
        @Valid @RequestBody AdminNotificationRequest request) {
        return ApiResponse.ok(broadcastService.create(admin.id(), request.content(), request.scheduledAt()));
    }

    @Operation(summary = "알림 상세")
    @GetMapping("/{broadcastId}")
    public ApiResponse<Broadcast> get(@PathVariable Long broadcastId) {
        return ApiResponse.ok(broadcastService.get(broadcastId));
    }

    @Operation(summary = "예약 수정", description = "발송을 시작하기 전에만 바꿀 수 있다(아니면 409). 값을 모두 보낸다.")
    @PutMapping("/{broadcastId}")
    public ApiResponse<Broadcast> update(@PathVariable Long broadcastId,
        @Valid @RequestBody AdminNotificationRequest request) {
        return ApiResponse.ok(broadcastService.update(broadcastId, request.content(), request.scheduledAt()));
    }

    @Operation(summary = "예약 취소", description = "발송을 시작하기 전에만 취소할 수 있다(아니면 409).")
    @PostMapping("/{broadcastId}/cancel")
    public ApiResponse<Broadcast> cancel(@PathVariable Long broadcastId) {
        return ApiResponse.ok(broadcastService.cancel(broadcastId));
    }

    @Operation(summary = "테스트 발송", description = "작성 중인 알림을 테스트 대상(TEST_NOTIFICATION_USER_IDS)의 기기로 바로 "
        + "보낸다. 수신 설정·동의와 상관없이 보낸다. 대상이 설정되어 있지 않으면 409.")
    @PostMapping("/test")
    public ApiResponse<TestSend> sendTest(@Valid @RequestBody AdminNotificationTestRequest request) {
        return ApiResponse.ok(broadcastService.sendTest(request.content()));
    }

    @Operation(summary = "발송 이력", description = "기본은 오늘까지 최근 30일, 최대 92일. 수동 발송은 건별, 자동 알림은 서비스일·"
        + "유형별 건수다. 알림을 끈 사람은 성공·실패에 세지 않는다. 알림은 30일 뒤 지우므로 자동 알림은 최근 30일만 남는다.")
    @GetMapping("/history")
    public ApiResponse<History> history(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(broadcastService.history(from, to));
    }
}
