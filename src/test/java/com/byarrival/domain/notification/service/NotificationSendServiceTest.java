package com.byarrival.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationCategory;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.entity.PushStatus;
import com.byarrival.domain.notification.event.NotificationCreatedEvent;
import com.byarrival.domain.notification.repository.NotificationRepository;
import com.byarrival.domain.user.entity.NotificationPermission;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationSendServiceTest {

    private static final Long USER_ID = 1L;
    // 03:00은 아직 전날 서비스일이다.
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 3, 0);

    private NotificationRepository notificationRepository;
    private NotificationSettingService settingService;
    private UserService userService;
    private ApplicationEventPublisher eventPublisher;
    private NotificationSendService sendService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        settingService = mock(NotificationSettingService.class);
        userService = mock(UserService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        sendService = new NotificationSendService(notificationRepository, settingService, userService, eventPublisher,
            clock);
        given(notificationRepository.save(any())).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 100L);
            return notification;
        });
    }

    @Test
    void 운영_안내는_알림함에_저장하고_커밋_후_푸시하도록_이벤트를_발행한다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.of(NotificationPermission.GRANTED));
        given(settingService.isEnabled(USER_ID, NotificationCategory.ACTIVITY)).willReturn(true);

        Optional<Long> id = sendService.send(notice());

        Notification saved = savedNotification();
        assertThat(id).contains(100L);
        assertThat(saved.getPushStatus()).isEqualTo(PushStatus.PENDING);
        assertThat(saved.getServiceDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        verify(eventPublisher).publishEvent(new NotificationCreatedEvent(100L));
    }

    @Test
    void 수신_설정을_끄면_운영_안내도_알림함에만_저장하고_푸시하지_않는다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.of(NotificationPermission.GRANTED));
        given(settingService.isEnabled(USER_ID, NotificationCategory.ACTIVITY)).willReturn(false);

        Optional<Long> id = sendService.send(notice());

        assertThat(id).contains(100L);
        assertThat(savedNotification().getPushStatus()).isEqualTo(PushStatus.SKIPPED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void OS_알림_권한을_거부했으면_알림함에만_저장하고_푸시하지_않는다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.of(NotificationPermission.DENIED));

        Optional<Long> id = sendService.send(notice());

        assertThat(id).contains(100L);
        assertThat(savedNotification().getPushStatus()).isEqualTo(PushStatus.SKIPPED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void OS_알림_권한을_아직_묻지_않았으면_푸시한다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.empty());
        given(settingService.isEnabled(USER_ID, NotificationCategory.ACTIVITY)).willReturn(true);

        sendService.send(notice());

        assertThat(savedNotification().getPushStatus()).isEqualTo(PushStatus.PENDING);
    }

    @Test
    void 테스트_알림은_일반_발송으로_보낼_수_없다() {
        NotificationRequest request = NotificationRequest.of(USER_ID, NotificationType.TEST, "제목", "내용");

        assertThatThrownBy(() -> sendService.send(request)).isInstanceOf(IllegalStateException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 행동_유도_알림은_수신_설정이_켜져_있으면_푸시한다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.of(NotificationPermission.GRANTED));
        given(settingService.isEnabled(USER_ID, NotificationCategory.ACTIVITY)).willReturn(true);

        Optional<Long> id = sendService.send(routeReady());

        assertThat(id).contains(100L);
        assertThat(savedNotification().getPushStatus()).isEqualTo(PushStatus.PENDING);
        verify(eventPublisher).publishEvent(new NotificationCreatedEvent(100L));
    }

    @Test
    void 행동_유도_알림은_수신_설정이_꺼져_있으면_알림함에만_저장한다() {
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.of(NotificationPermission.GRANTED));
        given(settingService.isEnabled(USER_ID, NotificationCategory.ACTIVITY)).willReturn(false);

        sendService.send(routeReady());

        assertThat(savedNotification().getPushStatus()).isEqualTo(PushStatus.SKIPPED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 같은_서비스일에_같은_유형을_이미_보냈으면_보내지_않는다() {
        given(notificationRepository.existsByUserIdAndTypeAndServiceDate(
            USER_ID, NotificationType.ROUTE_READY, LocalDate.of(2026, 11, 8))).willReturn(true);

        assertThat(sendService.send(routeReady())).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 행동_유도_알림을_하루_2번_보냈으면_더_보내지_않는다() {
        given(notificationRepository.countByUserIdAndServiceDateAndTypeIn(
            USER_ID, LocalDate.of(2026, 11, 8), NotificationType.actionTypes())).willReturn(2L);

        assertThat(sendService.send(routeReady())).isEmpty();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void 운영_안내는_하루_발송_횟수와_상관없이_보낸다() {
        given(notificationRepository.countByUserIdAndServiceDateAndTypeIn(any(), any(), any())).willReturn(2L);
        given(userService.findNotificationPermission(USER_ID)).willReturn(Optional.empty());

        assertThat(sendService.send(notice())).contains(100L);
    }

    @Test
    void 테스트_알림은_설정과_상관없이_푸시할_상태로_저장한다() {
        Long id = sendService.createTest(USER_ID, "테스트", "내용");

        Notification saved = savedNotification();
        assertThat(id).isEqualTo(100L);
        assertThat(saved.getType()).isEqualTo(NotificationType.TEST);
        assertThat(saved.getPushStatus()).isEqualTo(PushStatus.PENDING);
        verify(eventPublisher, never()).publishEvent(any());
        verifyNoInteractions(userService, settingService);
    }

    private NotificationRequest notice() {
        return NotificationRequest.of(USER_ID, NotificationType.SERVICE_NOTICE, "점검 안내", "02:00~03:00 점검");
    }

    private NotificationRequest routeReady() {
        return NotificationRequest.of(USER_ID, NotificationType.ROUTE_READY, "노선이 준비됐어요", "출근길 기사 3개");
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }
}
