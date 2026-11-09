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

    /** FCM 멀티캐스트 한 번에 넣을 수 있는 최대 토큰 수 */
    static final int MAX_TOKENS_PER_MULTICAST = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final String androidChannelId;

    /**
     * 토큰을 500개씩 나눠 보내고 결과를 합친다. 일부 묶음이 실패해도 나머지 묶음 결과는 돌려주고,
     * 모든 묶음이 실패했을 때만 예외를 던진다.
     */
    @Override
    public PushResult send(List<String> tokens, PushMessage message) {
        if (tokens.isEmpty()) {
            return PushResult.of(0, List.of());
        }
        int successCount = 0;
        List<String> invalid = new ArrayList<>();
        PushSendException lastFailure = null;
        int failedBatches = 0;
        int batches = 0;
        for (int from = 0; from < tokens.size(); from += MAX_TOKENS_PER_MULTICAST) {
            List<String> batch = tokens.subList(from, Math.min(from + MAX_TOKENS_PER_MULTICAST, tokens.size()));
            batches++;
            try {
                BatchResponse response = firebaseMessaging.sendEachForMulticast(toMulticast(batch, message));
                successCount += response.getSuccessCount();
                invalid.addAll(invalidTokens(batch, response.getResponses()));
            } catch (FirebaseMessagingException e) {
                failedBatches++;
                lastFailure = new PushSendException("FCM 발송에 실패했습니다: " + e.getMessagingErrorCode(), e);
                log.warn("FCM 묶음 발송 실패: batchSize={}, errorCode={}", batch.size(), e.getMessagingErrorCode());
            }
        }
        if (failedBatches == batches) {
            throw lastFailure;
        }
        return PushResult.of(successCount, invalid);
    }

    // firebase-admin 9.10.0부터 토큰 지정이 FID(Firebase Installation ID) 권장으로 deprecated 표시됐다.
    // 앱 SDK가 아직 등록 토큰을 주고 FCM도 토큰 발송을 지원하므로 토큰을 쓴다. 앱이 FID로 바꾸면 함께 바꾼다.
    @SuppressWarnings("deprecation")
    private MulticastMessage toMulticast(List<String> tokens, PushMessage message) {
        return MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder().setTitle(message.title()).setBody(message.body()).build())
            .putAllData(message.data())
            .setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder().setChannelId(androidChannelId).build())
                .build())
            .build();
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
