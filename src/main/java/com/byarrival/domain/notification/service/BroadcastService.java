package com.byarrival.domain.notification.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.notification.config.BroadcastProperties;
import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.AutoHistory;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.Broadcast;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.Estimate;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.History;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.TestResult;
import com.byarrival.domain.notification.dto.response.BroadcastResponses.TestSend;
import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import com.byarrival.domain.notification.entity.NotificationBroadcast;
import com.byarrival.domain.notification.entity.NotificationBroadcast.BroadcastContent;
import com.byarrival.domain.notification.entity.NotificationScreen;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.event.BroadcastScheduledEvent;
import com.byarrival.domain.notification.exception.NotificationErrorCode;
import com.byarrival.domain.notification.repository.AutoNotificationHistoryRepository;
import com.byarrival.domain.notification.repository.BroadcastRecipientRepository;
import com.byarrival.domain.notification.repository.NotificationBroadcastRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 어드민 알림 작성·예약·수정·취소·테스트 발송·발송 이력(NOTI-01·02).
 * <ul>
 *     <li>예약 시각이 없으면 즉시 발송이다. 예약 시각은 분 단위로 받고 지금보다 뒤여야 한다.</li>
 *     <li>받을 사람이 0명이면 저장하지 않는다. 보낼 때 대상을 다시 고른다.</li>
 *     <li>발송을 시작하기 전까지만 수정·취소할 수 있다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BroadcastService {

    static final int DEFAULT_HISTORY_DAYS = 30;
    static final int MAX_HISTORY_DAYS = 92;
    static final List<NotificationType> AUTO_TYPES = List.of(NotificationType.ROUTE_READY,
        NotificationType.ROUTE_INCOMPLETE, NotificationType.STREAK_MILESTONE, NotificationType.SMALLTALK,
        NotificationType.STREAK_BREAK, NotificationType.POPULAR_ARTICLE);

    private final NotificationBroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final AutoNotificationHistoryRepository autoHistoryRepository;
    private final NotificationSendService sendService;
    private final PushDispatcher pushDispatcher;
    private final BroadcastProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @param scheduledAt 예약 시각. 없으면 지금
     */
    @Transactional(readOnly = true)
    public Estimate estimate(BroadcastKind kind, BroadcastTarget target, LocalDateTime scheduledAt) {
        LocalDateTime sendAt = scheduledAt == null ? now() : scheduledAt;
        boolean night = BroadcastPolicy.isNight(sendAt);
        return new Estimate(recipientRepository.count(kind, target, night, now()), night);
    }

    /**
     * @param scheduledAt 예약 시각. 없으면 즉시 발송
     */
    @Transactional
    public Broadcast create(Long adminId, BroadcastContent content, LocalDateTime scheduledAt) {
        LocalDateTime sendAt = sendTimeOf(scheduledAt);
        validate(content, sendAt);
        NotificationBroadcast saved = broadcastRepository.save(NotificationBroadcast.schedule(adminId, content, sendAt));
        eventPublisher.publishEvent(new BroadcastScheduledEvent(saved.getId()));
        return Broadcast.of(saved, now());
    }

    /**
     * 발송을 시작하기 전에만 바꿀 수 있다. 예약 시각을 비우면 지금 보낸다.
     */
    @Transactional
    public Broadcast update(Long broadcastId, BroadcastContent content, LocalDateTime scheduledAt) {
        NotificationBroadcast broadcast = getEditable(broadcastId);
        LocalDateTime sendAt = sendTimeOf(scheduledAt);
        validate(content, sendAt);
        broadcast.edit(content, sendAt);
        eventPublisher.publishEvent(new BroadcastScheduledEvent(broadcastId));
        return Broadcast.of(broadcast, now());
    }

    @Transactional
    public Broadcast cancel(Long broadcastId) {
        NotificationBroadcast broadcast = getEditable(broadcastId);
        broadcast.cancel();
        return Broadcast.of(broadcast, now());
    }

    @Transactional(readOnly = true)
    public Broadcast get(Long broadcastId) {
        return Broadcast.of(broadcastRepository.findById(broadcastId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.BROADCAST_NOT_FOUND)), now());
    }

    /**
     * 발송 이력. 기간을 보내지 않으면 오늘(서비스일)까지 최근 30일이다.
     */
    @Transactional(readOnly = true)
    public History history(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? ServiceDay.today(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_HISTORY_DAYS - 1L) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_HISTORY_DAYS) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_INVALID_PERIOD);
        }
        LocalDateTime now = now();
        List<Broadcast> manual = broadcastRepository.findScheduledBetween(start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()).stream()
            .map(broadcast -> Broadcast.of(broadcast, now))
            .toList();
        List<AutoHistory> auto = autoHistoryRepository.findDaily(AUTO_TYPES, start, end).stream()
            .map(row -> new AutoHistory(row.serviceDate(), row.type(), row.success(), row.failure(), row.pending()))
            .toList();
        return new History(start, end, manual, auto);
    }

    /**
     * 작성 중인 알림을 테스트 대상 기기로 바로 보낸다(NOTI-02). 수신 설정·동의와 상관없이 보낸다.
     */
    public TestSend sendTest(BroadcastContent content) {
        validateLink(content);
        List<Long> userIds = properties.testUserIds();
        if (userIds.isEmpty()) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_NO_TEST_TARGET);
        }
        NotificationRequest request = BroadcastDispatcher.requestOf(content, null, null);
        List<TestResult> results = new ArrayList<>();
        for (Long userId : userIds) {
            Long notificationId = sendService.createTest(userId, request.title(), request.body(), request.params());
            results.add(new TestResult(userId, pushDispatcher.dispatch(notificationId)));
        }
        return new TestSend(results);
    }

    private NotificationBroadcast getEditable(Long broadcastId) {
        NotificationBroadcast broadcast = broadcastRepository.findForUpdate(broadcastId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.BROADCAST_NOT_FOUND));
        if (!broadcast.isEditable(now())) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_NOT_EDITABLE);
        }
        return broadcast;
    }

    // 예약 시각은 분 단위로 받는다. 없으면 지금 보낸다
    private LocalDateTime sendTimeOf(LocalDateTime scheduledAt) {
        LocalDateTime now = now();
        if (scheduledAt == null) {
            return now;
        }
        LocalDateTime minute = scheduledAt.truncatedTo(ChronoUnit.MINUTES);
        if (!minute.isAfter(now)) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_PAST_SCHEDULE);
        }
        return minute;
    }

    private void validate(BroadcastContent content, LocalDateTime sendAt) {
        validateLink(content);
        if (recipientRepository.count(content.kind(), content.target(), BroadcastPolicy.isNight(sendAt), now()) == 0) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_NO_RECIPIENT);
        }
    }

    // 공지사항 화면은 웹뷰 주소가 필요하고, 다른 화면에는 주소를 넣지 않는다
    private static void validateLink(BroadcastContent content) {
        boolean notice = content.screen() == NotificationScreen.NOTICE;
        boolean hasUrl = content.url() != null && !content.url().isBlank();
        if (notice != hasUrl || (hasUrl && !content.url().startsWith("https://"))) {
            throw new BusinessException(NotificationErrorCode.BROADCAST_INVALID_LINK);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
