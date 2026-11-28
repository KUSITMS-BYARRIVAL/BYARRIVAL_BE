package com.byarrival.domain.notification.event;

import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.service.NotificationSendService;
import com.byarrival.domain.term.event.MarketingConsentChangedEvent;
import com.byarrival.domain.term.event.MarketingConsentReconfirmEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 광고성 정보 수신 동의 안내(정보통신망법). 동의·철회가 저장된 뒤 처리 결과를, 동의 후 2년이 지나면 동의 여부를 알림함에 남긴다.
 * 안내에 실패해도 동의 변경은 되돌리지 않는다. 알림함에는 늘 남고, 푸시는 알림 수신 설정을 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketingConsentNotificationListener {

    static final String TITLE = "광고성 정보 수신 동의 안내";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final NotificationSendService sendService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onChanged(MarketingConsentChangedEvent event) {
        send(event.userId(), changedBody(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReconfirm(MarketingConsentReconfirmEvent event) {
        send(event.userId(), reconfirmBody(event.agreedAt()));
    }

    static String changedBody(MarketingConsentChangedEvent event) {
        List<String> results = new ArrayList<>();
        if (event.marketing() != null) {
            results.add("광고성 정보 수신 " + (event.marketing() ? "동의" : "철회"));
        }
        if (event.night() != null) {
            results.add("야간(21~08시) 광고성 정보 수신 " + (event.night() ? "동의" : "철회"));
        }
        return "[도착까지] %s에 %s가 처리됐어요.".formatted(DATE.format(event.changedAt()), String.join(", ", results));
    }

    static String reconfirmBody(LocalDateTime agreedAt) {
        return "[도착까지] %s에 광고성 정보 수신에 동의하셨어요. 계속 받지 않으려면 마이에서 언제든 철회할 수 있어요."
            .formatted(DATE.format(agreedAt));
    }

    private void send(Long userId, String body) {
        try {
            sendService.send(NotificationRequest.of(userId, NotificationType.MARKETING_CONSENT, TITLE, body));
        } catch (RuntimeException e) {
            log.error("[MarketingConsent] 수신 동의 안내 저장 실패: userId={}", userId, e);
        }
    }
}
