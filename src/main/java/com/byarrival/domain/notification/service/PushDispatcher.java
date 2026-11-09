package com.byarrival.domain.notification.service;

import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.PushStatus;
import com.byarrival.domain.notification.push.PushMessage;
import com.byarrival.domain.notification.push.PushResult;
import com.byarrival.domain.notification.push.PushSender;
import com.byarrival.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 저장된 알림 하나를 그 사용자의 모든 기기로 푸시하고 결과를 기록한다.
 * 외부 호출이 있어 트랜잭션 없이 실행하고, 조회·결과 저장만 각각 짧은 트랜잭션으로 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    static final String NOTIFICATION_ID_KEY = "notificationId";
    static final String TYPE_KEY = "type";

    private final NotificationRepository notificationRepository;
    private final DeviceTokenService deviceTokenService;
    private final PushSender pushSender;
    private final Clock clock;

    /**
     * @return 푸시를 받은 기기 수
     */
    public int dispatch(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            log.warn("푸시할 알림이 없습니다: notificationId={}", notificationId);
            return 0;
        }
        List<String> tokens = deviceTokenService.findTokens(notification.getUserId());
        if (tokens.isEmpty()) {
            updateStatus(notificationId, PushStatus.NO_DEVICE);
            return 0;
        }

        PushResult result;
        try {
            result = pushSender.send(tokens, toMessage(notification));
        } catch (RuntimeException e) {
            log.error("푸시 발송 실패: notificationId={}", notificationId, e);
            updateStatus(notificationId, PushStatus.FAILED);
            return 0;
        }

        deviceTokenService.deleteTokens(result.invalidTokens());
        updateStatus(notificationId, result.successCount() > 0 ? PushStatus.SENT : PushStatus.FAILED);
        return result.successCount();
    }

    private PushMessage toMessage(Notification notification) {
        Map<String, String> data = new LinkedHashMap<>(notification.getParams());
        data.put(NOTIFICATION_ID_KEY, String.valueOf(notification.getId()));
        data.put(TYPE_KEY, notification.getType().name());
        return new PushMessage(notification.getTitle(), notification.getBody(), data);
    }

    private void updateStatus(Long notificationId, PushStatus status) {
        notificationRepository.updatePushStatus(notificationId, status, LocalDateTime.now(clock));
    }
}
