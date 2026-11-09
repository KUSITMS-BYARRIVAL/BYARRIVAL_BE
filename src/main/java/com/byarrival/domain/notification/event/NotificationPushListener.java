package com.byarrival.domain.notification.event;

import com.byarrival.common.config.AsyncConfig;
import com.byarrival.domain.notification.service.PushDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장이 커밋된 뒤 푸시를 보낸다. 푸시 실패가 알림을 만든 쪽의 작업을 실패시키지 않도록 별도 스레드에서 실행한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final PushDispatcher pushDispatcher;

    @Async(AsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        pushDispatcher.dispatch(event.notificationId());
    }
}
