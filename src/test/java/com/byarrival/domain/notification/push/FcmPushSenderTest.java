package com.byarrival.domain.notification.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class FcmPushSenderTest {

    private static final PushMessage MESSAGE = new PushMessage("제목", "내용", Map.of());

    private FirebaseMessaging firebaseMessaging;
    private FcmPushSender sender;

    @BeforeEach
    void setUp() {
        firebaseMessaging = mock(FirebaseMessaging.class);
        sender = new FcmPushSender(firebaseMessaging, "default");
    }

    @Test
    void 토큰이_500개를_넘으면_500개씩_나눠_보내고_결과를_합친다() throws Exception {
        List<String> tokens = tokens(501);
        BatchResponse first = batchResponse(500, 0);
        BatchResponse second = batchResponse(0, 1);
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(first, second);

        PushResult result = sender.send(tokens, MESSAGE);

        ArgumentCaptor<MulticastMessage> captor = ArgumentCaptor.forClass(MulticastMessage.class);
        verify(firebaseMessaging, times(2)).sendEachForMulticast(captor.capture());
        assertThat(tokensOf(captor.getAllValues().get(0))).hasSize(500).isEqualTo(tokens.subList(0, 500));
        assertThat(tokensOf(captor.getAllValues().get(1))).containsExactly("token-500");
        assertThat(result.successCount()).isEqualTo(500);
        assertThat(result.invalidTokens()).containsExactly("token-500");
    }

    @Test
    void 일부_묶음만_실패하면_성공한_묶음의_결과를_돌려준다() throws Exception {
        FirebaseMessagingException failure = firebaseException();
        BatchResponse success = batchResponse(1, 0);
        given(firebaseMessaging.sendEachForMulticast(any())).willThrow(failure).willReturn(success);

        PushResult result = sender.send(tokens(501), MESSAGE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).isEmpty();
    }

    @Test
    void 모든_묶음이_실패하면_발송_실패_예외를_던진다() throws Exception {
        FirebaseMessagingException failure = firebaseException();
        given(firebaseMessaging.sendEachForMulticast(any())).willThrow(failure);

        assertThatThrownBy(() -> sender.send(tokens(501), MESSAGE)).isInstanceOf(PushSendException.class);
    }

    @Test
    void 오류_코드가_없는_기기_실패는_무효_토큰으로_보지_않고_나머지_결과를_돌려준다() throws Exception {
        SendResponse success = mock(SendResponse.class);
        given(success.isSuccessful()).willReturn(true);
        SendResponse unknownFailure = sendFailure(null);
        SendResponse unregistered = sendFailure(MessagingErrorCode.UNREGISTERED);
        BatchResponse response = mock(BatchResponse.class);
        given(response.getSuccessCount()).willReturn(1);
        given(response.getResponses()).willReturn(List.of(success, unknownFailure, unregistered));
        given(firebaseMessaging.sendEachForMulticast(any())).willReturn(response);

        PushResult result = sender.send(List.of("ok", "unknown", "gone"), MESSAGE);

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.invalidTokens()).containsExactly("gone");
    }

    @Test
    void 토큰이_없으면_발송하지_않는다() throws Exception {
        PushResult result = sender.send(List.of(), MESSAGE);

        assertThat(result.successCount()).isZero();
        verify(firebaseMessaging, times(0)).sendEachForMulticast(any());
    }

    private List<String> tokens(int count) {
        return IntStream.range(0, count).mapToObj(i -> "token-" + i).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> tokensOf(MulticastMessage message) {
        return (List<String>) ReflectionTestUtils.getField(message, "tokens");
    }

    /**
     * 앞의 successCount개는 성공, 뒤의 unregisteredCount개는 UNREGISTERED 실패인 응답
     */
    private BatchResponse batchResponse(int successCount, int unregisteredCount) {
        List<SendResponse> responses = new ArrayList<>();
        for (int i = 0; i < successCount; i++) {
            SendResponse success = mock(SendResponse.class);
            given(success.isSuccessful()).willReturn(true);
            responses.add(success);
        }
        for (int i = 0; i < unregisteredCount; i++) {
            responses.add(sendFailure(MessagingErrorCode.UNREGISTERED));
        }
        BatchResponse response = mock(BatchResponse.class);
        given(response.getSuccessCount()).willReturn(successCount);
        given(response.getResponses()).willReturn(responses);
        return response;
    }

    private SendResponse sendFailure(MessagingErrorCode errorCode) {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(errorCode);
        SendResponse failure = mock(SendResponse.class);
        given(failure.isSuccessful()).willReturn(false);
        given(failure.getException()).willReturn(exception);
        return failure;
    }

    private FirebaseMessagingException firebaseException() {
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNAVAILABLE);
        return exception;
    }
}
