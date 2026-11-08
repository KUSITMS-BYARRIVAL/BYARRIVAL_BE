package com.byarrival.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.notification.dto.response.NotificationPageResponse;
import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.exception.NotificationErrorCode;
import com.byarrival.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 9, 0);
    private static final LocalDateTime SINCE = NOW.minusDays(30);

    private NotificationRepository notificationRepository;
    private NotificationSendService sendService;
    private DeviceTokenService deviceTokenService;
    private PushDispatcher pushDispatcher;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        sendService = mock(NotificationSendService.class);
        deviceTokenService = mock(DeviceTokenService.class);
        pushDispatcher = mock(PushDispatcher.class);
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(ZonedDateTime.of(NOW, kst).toInstant(), kst);
        notificationService = new NotificationService(notificationRepository, sendService, deviceTokenService,
            pushDispatcher, clock);
    }

    @Test
    void 요청한_개수보다_하나_더_있으면_다음_커서로_마지막_항목_id를_준다() {
        given(notificationRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            USER_ID, SINCE, Limit.of(3))).willReturn(notifications(10, 9, 8));

        NotificationPageResponse page = notificationService.getNotifications(USER_ID, null, 2);

        assertThat(page.notifications()).extracting("id").containsExactly(10L, 9L);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.nextCursor()).isEqualTo(9L);
    }

    @Test
    void 커서가_있으면_그보다_오래된_알림을_주고_마지막_페이지면_다음_커서가_없다() {
        given(notificationRepository.findByUserIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
            USER_ID, 9L, SINCE, Limit.of(3))).willReturn(notifications(8));

        NotificationPageResponse page = notificationService.getNotifications(USER_ID, 9L, 2);

        assertThat(page.notifications()).extracting("id").containsExactly(8L);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void 개수를_안_주면_20개_최대보다_크면_50개로_조회한다() {
        given(notificationRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            eq(USER_ID), eq(SINCE), eq(Limit.of(21)))).willReturn(List.of());
        given(notificationRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(
            eq(USER_ID), eq(SINCE), eq(Limit.of(51)))).willReturn(List.of());

        assertThat(notificationService.getNotifications(USER_ID, null, null).notifications()).isEmpty();
        assertThat(notificationService.getNotifications(USER_ID, null, 1000).notifications()).isEmpty();
        verify(notificationRepository).findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(USER_ID, SINCE,
            Limit.of(21));
        verify(notificationRepository).findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(USER_ID, SINCE,
            Limit.of(51));
    }

    @Test
    void 내_알림이_아니면_읽음_처리할_때_NOTIFICATION_NOT_FOUND로_응답한다() {
        given(notificationRepository.findByIdAndUserId(5L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(USER_ID, 5L))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void 이미_읽은_알림은_읽은_시각을_바꾸지_않는다() {
        Notification notification = notifications(5).getFirst();
        notification.markRead(NOW.minusHours(1));
        given(notificationRepository.findByIdAndUserId(5L, USER_ID)).willReturn(Optional.of(notification));

        notificationService.markRead(USER_ID, 5L);

        assertThat(notification.getReadAt()).isEqualTo(NOW.minusHours(1));
    }

    @Test
    void 등록된_기기가_없으면_테스트_발송은_NO_REGISTERED_DEVICE로_응답한다() {
        given(deviceTokenService.hasDevice(USER_ID)).willReturn(false);

        assertThatThrownBy(() -> notificationService.sendTest(USER_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(NotificationErrorCode.NO_REGISTERED_DEVICE);
        verify(sendService, never()).createTest(anyLong(), anyString(), anyString());
    }

    @Test
    void 테스트_발송은_알림을_저장한_뒤_바로_푸시하고_받은_기기_수를_돌려준다() {
        given(deviceTokenService.hasDevice(USER_ID)).willReturn(true);
        given(sendService.createTest(eq(USER_ID), anyString(), anyString())).willReturn(100L);
        given(pushDispatcher.dispatch(100L)).willReturn(2);

        assertThat(notificationService.sendTest(USER_ID).sentDeviceCount()).isEqualTo(2);
    }

    @Test
    void 보관_기간이_지난_알림을_지운다() {
        given(notificationRepository.deleteCreatedBefore(SINCE)).willReturn(3);

        assertThat(notificationService.deleteExpired()).isEqualTo(3);
    }

    private List<Notification> notifications(long... ids) {
        return LongStream.of(ids).mapToObj(id -> {
            Notification notification = Notification.create(USER_ID, NotificationType.SERVICE_NOTICE, "제목", "내용",
                Map.of(), LocalDate.of(2026, 11, 8), true);
            ReflectionTestUtils.setField(notification, "id", id);
            return notification;
        }).toList();
    }
}
