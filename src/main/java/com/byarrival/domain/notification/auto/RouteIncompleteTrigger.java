package com.byarrival.domain.notification.auto;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.route.repository.IdleRoute;
import com.byarrival.domain.route.service.RouteNotificationQueryService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 미완주 노선 리마인드. 노선을 만들었지만 도착하지 않았고, 마지막 활동(노선 생성·보딩·출발·기사 보러가기) 뒤
 * 정해진 시간 동안 움직임이 없으면 보낸다. 만료된 노선과 오늘 이미 보낸 회원은 뺀다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteIncompleteTrigger {

    private final AutoNotificationProperties properties;
    private final RouteNotificationQueryService routeQueryService;
    private final NotificationMessages messages;
    private final AutoNotificationSender sender;

    public void run(LocalDateTime now) {
        LocalDateTime to = now.minusMinutes(properties.routeIncompleteIdleMinutes());
        LocalDateTime from = to.truncatedTo(ChronoUnit.MINUTES).minusMinutes(properties.graceMinutes());
        List<IdleRoute> routes = routeQueryService.findIdleRoutes(ServiceDay.of(now), now, from, to);
        for (IdleRoute route : routes) {
            try {
                send(route);
            } catch (RuntimeException e) {
                log.error("[AutoNotification] 미완주 리마인드 처리 실패: routeId={}", route.getRouteId(), e);
            }
        }
    }

    private void send(IdleRoute route) {
        if (sender.isSentToday(route.getUserId(), NotificationType.ROUTE_INCOMPLETE)) {
            return;
        }
        sender.send(route.getUserId(), NotificationType.ROUTE_INCOMPLETE,
            messages.routeIncompleteTitle(Math.toIntExact(route.getRemainingStops())),
            NotificationMessages.ROUTE_INCOMPLETE_BODY, Map.of());
    }
}
