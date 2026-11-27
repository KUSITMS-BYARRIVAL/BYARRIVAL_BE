package com.byarrival.domain.notification.auto;

import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.route.event.RouteCompletedEvent;
import com.byarrival.domain.route.service.RouteStreakService;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 연속 출근길 달성 알림. 완주로 스트릭이 정해진 일수(3·7·14·30일)가 되면 바로 보낸다.
 * 쉬는 날의 완주도 일수에 들어가므로 요일과 상관없이 확인한다. 알림 실패가 완주 응답을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreakMilestoneListener {

    private final AutoNotificationProperties properties;
    private final RouteStreakService routeStreakService;
    private final NotificationMessages messages;
    private final AutoNotificationSender sender;

    @EventListener
    public void onRouteCompleted(RouteCompletedEvent event) {
        try {
            notifyMilestone(event.userId(), event.serviceDate());
        } catch (RuntimeException e) {
            log.error("[AutoNotification] 연속 출근길 달성 알림 처리 실패: userId={}", event.userId(), e);
        }
    }

    private void notifyMilestone(Long userId, LocalDate serviceDate) {
        int streakDays = routeStreakService.streakDays(userId, serviceDate);
        if (!properties.streakMilestones().contains(streakDays)) {
            return;
        }
        int longestDays = routeStreakService.longestStreakDays(userId, serviceDate);
        sender.send(userId, NotificationType.STREAK_MILESTONE, messages.streakMilestoneTitle(streakDays),
            messages.streakMilestoneBody(streakDays, longestDays), Map.of());
    }
}
