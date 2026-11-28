package com.byarrival.domain.notification.event;

import com.byarrival.common.config.AsyncConfig;
import com.byarrival.domain.notification.service.BroadcastDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 즉시 발송은 저장이 커밋된 뒤 별도 스레드에서 바로 보낸다. 예약 시각이 안 된 발송은 예약 발송 작업이 보낸다.
 */
@Component
@RequiredArgsConstructor
public class BroadcastDispatchListener {

    private final BroadcastDispatcher dispatcher;

    @Async(AsyncConfig.PUSH_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScheduled(BroadcastScheduledEvent event) {
        dispatcher.dispatchIfDue(event.broadcastId());
    }
}
