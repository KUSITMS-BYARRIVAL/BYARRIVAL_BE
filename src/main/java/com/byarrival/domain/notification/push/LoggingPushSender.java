package com.byarrival.domain.notification.push;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM 없이 개발할 때 쓰는 발송기. 실제로 보내지 않고 로그만 남기며 모두 성공으로 본다.
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        log.info("[푸시 로그 발송] devices={}, title={}, data={}", tokens.size(), message.title(), message.data());
        return PushResult.of(tokens.size(), List.of());
    }
}
