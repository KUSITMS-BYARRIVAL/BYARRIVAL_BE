package com.byarrival.domain.notification.service;

import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.BroadcastStatus;
import com.byarrival.domain.notification.entity.NotificationBroadcast;
import com.byarrival.domain.notification.entity.NotificationBroadcast.BroadcastContent;
import com.byarrival.domain.notification.repository.BroadcastRecipientRepository;
import com.byarrival.domain.notification.repository.NotificationBroadcastRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예약 시각이 된 어드민 알림을 대상 회원에게 보낸다(NOTI-01). 발송을 시작할 때 대상을 다시 고른다.
 * 한 회원에서 실패해도 다른 회원에게는 계속 보낸다. 두 서버가 함께 돌아도 발송 시작은 한쪽만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastDispatcher {

    static final String BROADCAST_ID_KEY = "broadcastId";
    static final String SCREEN_KEY = "screen";
    static final String URL_KEY = "url";

    private final NotificationBroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final NotificationSendService sendService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @return 보낸 발송 수
     */
    public int dispatchDue() {
        int dispatched = 0;
        for (Long id : broadcastRepository.findDueIds(LocalDateTime.now(clock))) {
            if (dispatchIfDue(id)) {
                dispatched++;
            }
        }
        return dispatched;
    }

    /**
     * 예약 시각이 됐고 아직 시작하지 않은 발송이면 보낸다.
     *
     * @return 이번에 보냈으면 true
     */
    public boolean dispatchIfDue(Long broadcastId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Integer claimed = transactionTemplate.execute(status -> broadcastRepository.claim(broadcastId, now));
        if (claimed == null || claimed == 0) {
            return false;
        }
        NotificationBroadcast broadcast = broadcastRepository.findById(broadcastId).orElseThrow();
        int sent = 0;
        BroadcastStatus result = BroadcastStatus.COMPLETED;
        try {
            List<Long> recipients = recipientRepository.findRecipients(broadcast.getKind(), broadcast.getTarget(),
                BroadcastPolicy.isNight(now), now);
            NotificationRequest template = requestOf(broadcast, null);
            for (Long userId : recipients) {
                try {
                    if (sendService.send(requestOf(broadcast, userId)).isPresent()) {
                        sent++;
                    }
                } catch (RuntimeException e) {
                    log.error("[Broadcast] 알림 저장 실패: broadcastId={}, userId={}", broadcastId, userId, e);
                }
            }
            log.info("[Broadcast] broadcastId={} type={} 대상 {}명 중 {}명에게 보냄", broadcastId, template.type(),
                recipients.size(), sent);
        } catch (RuntimeException e) {
            log.error("[Broadcast] 발송 실패: broadcastId={}", broadcastId, e);
            result = BroadcastStatus.FAILED;
        }
        BroadcastStatus finalStatus = result;
        int finalSent = sent;
        transactionTemplate.executeWithoutResult(status -> broadcastRepository.finish(broadcastId,
            finalStatus.name(), finalSent, LocalDateTime.now(clock)));
        return true;
    }

    static NotificationRequest requestOf(NotificationBroadcast broadcast, Long userId) {
        return requestOf(broadcast.content(), broadcast.getId(), userId);
    }

    /**
     * @param broadcastId 발송 ID. 테스트 발송이면 null이라 결과를 세지 않는다
     */
    static NotificationRequest requestOf(BroadcastContent content, Long broadcastId, Long userId) {
        Map<String, String> params = new LinkedHashMap<>();
        if (broadcastId != null) {
            params.put(BROADCAST_ID_KEY, String.valueOf(broadcastId));
        }
        params.put(SCREEN_KEY, content.screen().name());
        if (content.url() != null) {
            params.put(URL_KEY, content.url());
        }
        return new NotificationRequest(userId, content.kind().getNotificationType(),
            content.kind().displayTitle(content.title()), content.body(), params);
    }
}
