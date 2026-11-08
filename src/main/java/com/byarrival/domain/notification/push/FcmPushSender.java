package com.byarrival.domain.notification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * FCM HTTP v1으로 푸시를 보낸다. 앱 삭제 등으로 등록이 풀린 토큰은 결과에 모아 돌려준다.
 */
@Slf4j
@RequiredArgsConstructor
public class FcmPushSender implements PushSender {

    private static final Set<MessagingErrorCode> INVALID_TOKEN_ERRORS =
        Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    private final FirebaseMessaging firebaseMessaging;
    private final String androidChannelId;

    // firebase-admin 9.10.0부터 토큰 지정이 FID(Firebase Installation ID) 권장으로 deprecated 표시됐다.
    // 앱 SDK가 아직 등록 토큰을 주고 FCM도 토큰 발송을 지원하므로 토큰을 쓴다. 앱이 FID로 바꾸면 함께 바꾼다.
    @SuppressWarnings("deprecation")
    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        if (tokens.isEmpty()) {
            return PushResult.of(0, List.of());
        }
        MulticastMessage multicast = MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
            .putAllData(message.data())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setChannelId(androidChannelId).build())
                .build())
            .build();
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(multicast);
            return PushResult.of(response.getSuccessCount(), invalidTokens(tokens, response.getResponses()));
        } catch (FirebaseMessagingException e) {
            throw new PushSendException("FCM 발송에 실패했습니다: " + e.getMessagingErrorCode(), e);
        }
    }

    private List<String> invalidTokens(List<String> tokens, List<SendResponse> responses) {
        List<String> invalid = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse response = responses.get(i);
            if (response.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = response.getException().getMessagingErrorCode();
            if (INVALID_TOKEN_ERRORS.contains(errorCode)) {
                invalid.add(tokens.get(i));
            } else {
                log.warn("FCM 기기 발송 실패: errorCode={}", errorCode);
            }
        }
        return invalid;
    }
}
