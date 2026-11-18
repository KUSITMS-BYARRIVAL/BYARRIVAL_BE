package com.byarrival.domain.notification.auto;

import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.smalltalk.service.SmalltalkQueryService;
import com.byarrival.domain.smalltalk.service.SmalltalkQueryService.SmalltalkView;
import com.byarrival.domain.user.service.UserNotificationTargetService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 오늘의 스몰토크 알림. 오늘의 스몰토크 목록(GET /smalltalks)의 첫 번째를 온보딩을 마친 회원 모두에게 보낸다.
 * 오늘 볼 수 있는 스몰토크가 없으면 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmalltalkNotificationTrigger {

    static final String SMALLTALK_ID_KEY = "smalltalkId";

    private final SmalltalkQueryService smalltalkQueryService;
    private final UserNotificationTargetService targetService;
    private final NotificationMessages messages;
    private final AutoNotificationSender sender;

    public void run(LocalDate today) {
        List<SmalltalkView> smalltalks = smalltalkQueryService.findAvailableOf(today);
        if (smalltalks.isEmpty()) {
            log.info("[AutoNotification] 오늘 볼 수 있는 스몰토크가 없어 알림을 보내지 않음: {}", today);
            return;
        }
        SmalltalkView first = smalltalks.getFirst();
        Map<String, String> params = Map.of(SMALLTALK_ID_KEY, String.valueOf(first.smalltalkId()));
        for (Long userId : targetService.findNotifiableUserIds()) {
            if (sender.isSentToday(userId, NotificationType.SMALLTALK)) {
                continue;
            }
            sender.send(userId, NotificationType.SMALLTALK, messages.smalltalkTitle(), first.hook(), params);
        }
    }
}
