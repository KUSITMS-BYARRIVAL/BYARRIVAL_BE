package com.byarrival.domain.notification.auto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.smalltalk.service.SmalltalkQueryService;
import com.byarrival.domain.smalltalk.service.SmalltalkQueryService.SmalltalkView;
import com.byarrival.domain.user.service.UserNotificationTargetService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class SmalltalkNotificationTriggerTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 9);

    private final SmalltalkQueryService smalltalkQueryService = mock(SmalltalkQueryService.class);
    private final UserNotificationTargetService targetService = mock(UserNotificationTargetService.class);
    private final AutoNotificationSender sender = mock(AutoNotificationSender.class);
    private final SmalltalkNotificationTrigger trigger = new SmalltalkNotificationTrigger(smalltalkQueryService,
        targetService, new NotificationMessages(new Random(1)), sender);

    @Test
    void 한_회원의_발송_여부_조회가_실패해도_다음_회원에게는_보낸다() {
        given(smalltalkQueryService.findAvailableOf(TODAY)).willReturn(List.of(smalltalk(45L)));
        given(targetService.findNotifiableUserIds()).willReturn(List.of(1L, 2L, 3L));
        given(sender.isSentToday(1L, NotificationType.SMALLTALK)).willThrow(new QueryTimeoutException("DB 지연"));
        given(sender.isSentToday(2L, NotificationType.SMALLTALK)).willReturn(true);

        trigger.run(TODAY);

        verify(sender, never()).send(eq(1L), any(), anyString(), anyString(), any());
        verify(sender, never()).send(eq(2L), any(), anyString(), anyString(), any());
        verify(sender).send(eq(3L), eq(NotificationType.SMALLTALK), anyString(), eq("\"한마디\""),
            eq(Map.of("smalltalkId", "45")));
    }

    private static SmalltalkView smalltalk(Long id) {
        return new SmalltalkView(id, "\"한마디\"", TODAY, 10L, 1L, "경제", "기사", "https://news.example.com/1", true,
            LocalDateTime.of(2030, 1, 8, 23, 50));
    }
}
