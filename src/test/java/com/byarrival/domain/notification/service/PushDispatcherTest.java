package com.byarrival.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.entity.PushStatus;
import com.byarrival.domain.notification.push.PushMessage;
import com.byarrival.domain.notification.push.PushResult;
import com.byarrival.domain.notification.push.PushSendException;
import com.byarrival.domain.notification.push.PushSender;
import com.byarrival.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class PushDispatcherTest {

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 9, 0);

    private NotificationRepository notificationRepository;
    private DeviceTokenService deviceTokenService;
    private PushSender pushSender;
    private PushDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        deviceTokenService = mock(DeviceTokenService.class);
        pushSender = mock(PushSender.class);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        dispatcher = new PushDispatcher(notificationRepository, deviceTokenService, pushSender, clock);

        Notification notification = Notification.create(USER_ID, NotificationType.SERVICE_NOTICE, "점검 안내", "내용",
            Map.of("noticeId", "7"), LocalDate.of(2026, 11, 8), true);
        ReflectionTestUtils.setField(notification, "id", NOTIFICATION_ID);
        given(notificationRepository.findById(NOTIFICATION_ID)).willReturn(Optional.of(notification));
    }

    @Test
    void 모든_기기로_보내고_알림_id와_유형을_data에_담는다() {
        given(deviceTokenService.findTokens(USER_ID)).willReturn(List.of("a", "b"));
        given(pushSender.send(anyList(), any())).willReturn(PushResult.of(2, List.of()));

        int sent = dispatcher.dispatch(NOTIFICATION_ID);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushSender).send(eq(List.of("a", "b")), message.capture());
        assertThat(sent).isEqualTo(2);
        assertThat(message.getValue().title()).isEqualTo("점검 안내");
        assertThat(message.getValue().data()).containsExactlyInAnyOrderEntriesOf(
            Map.of("notificationId", "100", "type", "SERVICE_NOTICE", "noticeId", "7"));
        verify(notificationRepository).updatePushStatus(NOTIFICATION_ID, PushStatus.SENT, NOW);
    }

    @Test
    void 등록이_풀린_토큰은_지운다() {
        given(deviceTokenService.findTokens(USER_ID)).willReturn(List.of("alive", "dead"));
        given(pushSender.send(anyList(), any())).willReturn(PushResult.of(1, List.of("dead")));

        dispatcher.dispatch(NOTIFICATION_ID);

        verify(deviceTokenService).deleteTokens(List.of("dead"));
        verify(notificationRepository).updatePushStatus(NOTIFICATION_ID, PushStatus.SENT, NOW);
    }

    @Test
    void 한_기기도_받지_못하면_실패로_기록한다() {
        given(deviceTokenService.findTokens(USER_ID)).willReturn(List.of("dead"));
        given(pushSender.send(anyList(), any())).willReturn(PushResult.of(0, List.of("dead")));

        int sent = dispatcher.dispatch(NOTIFICATION_ID);

        assertThat(sent).isZero();
        verify(notificationRepository).updatePushStatus(NOTIFICATION_ID, PushStatus.FAILED, NOW);
    }

    @Test
    void 등록된_기기가_없으면_보내지_않고_기기_없음으로_기록한다() {
        given(deviceTokenService.findTokens(USER_ID)).willReturn(List.of());

        int sent = dispatcher.dispatch(NOTIFICATION_ID);

        assertThat(sent).isZero();
        verify(pushSender, never()).send(anyList(), any());
        verify(notificationRepository).updatePushStatus(NOTIFICATION_ID, PushStatus.NO_DEVICE, NOW);
    }

    @Test
    void 푸시_서버_호출이_실패해도_예외를_던지지_않고_실패로_기록한다() {
        given(deviceTokenService.findTokens(USER_ID)).willReturn(List.of("a"));
        given(pushSender.send(anyList(), any())).willThrow(new PushSendException("down", null));

        int sent = dispatcher.dispatch(NOTIFICATION_ID);

        assertThat(sent).isZero();
        verify(notificationRepository).updatePushStatus(NOTIFICATION_ID, PushStatus.FAILED, NOW);
        verify(deviceTokenService, never()).deleteTokens(any());
    }

    @Test
    void 알림이_없으면_아무것도_하지_않는다() {
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        assertThat(dispatcher.dispatch(999L)).isZero();
        verify(pushSender, never()).send(anyList(), any());
    }
}
