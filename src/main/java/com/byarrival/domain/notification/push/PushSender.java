package com.byarrival.domain.notification.push;

import java.util.List;

/**
 * 기기 토큰으로 푸시를 보낸다. 외부 호출이므로 트랜잭션 밖에서 부른다.
 */
public interface PushSender {

    PushResult send(List<String> tokens, PushMessage message);
}
