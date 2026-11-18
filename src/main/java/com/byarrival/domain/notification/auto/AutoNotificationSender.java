package com.byarrival.domain.notification.auto;

import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.service.NotificationSendService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 자동 알림을 한 사용자씩 보낸다. 한 사용자의 실패가 같은 작업의 다른 사용자 발송을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoNotificationSender {

    private final NotificationSendService notificationSendService;

    public boolean isSentToday(Long userId, NotificationType type) {
        return notificationSendService.isSentToday(userId, type);
    }

    public void send(Long userId, NotificationType type, String title, String body, Map<String, String> params) {
        try {
            notificationSendService.send(new NotificationRequest(userId, type, title, body, params));
        } catch (DataIntegrityViolationException e) {
            // 블루/그린 전환 중 두 서버가 같은 알림을 동시에 저장하면 하나는 유니크 인덱스에 걸린다
            log.info("[AutoNotification] 이미 보낸 알림이라 건너뜀: userId={}, type={}", userId, type);
        } catch (RuntimeException e) {
            log.error("[AutoNotification] 알림 발송 실패: userId={}, type={}", userId, type, e);
        }
    }
}
