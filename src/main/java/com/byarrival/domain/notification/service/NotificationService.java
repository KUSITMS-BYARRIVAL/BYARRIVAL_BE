package com.byarrival.domain.notification.service;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.domain.notification.dto.response.NotificationPageResponse;
import com.byarrival.domain.notification.dto.response.NotificationResponse;
import com.byarrival.domain.notification.dto.response.TestPushResponse;
import com.byarrival.domain.notification.dto.response.UnreadCountResponse;
import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.exception.NotificationErrorCode;
import com.byarrival.domain.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림함 조회·읽음 처리와 테스트 발송. 알림은 30일 동안만 보여 주고 그 뒤에 지운다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final Period RETENTION = Period.ofDays(30);
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    static final String TEST_TITLE = "테스트 알림";
    static final String TEST_BODY = "도착까지 푸시 알림이 잘 도착했어요.";

    private final NotificationRepository notificationRepository;
    private final NotificationSendService notificationSendService;
    private final DeviceTokenService deviceTokenService;
    private final PushDispatcher pushDispatcher;
    private final Clock clock;

    /**
     * 최신순으로 size개를 돌려준다. cursor가 있으면 그 id보다 오래된 알림부터 준다.
     */
    @Transactional(readOnly = true)
    public NotificationPageResponse getNotifications(Long userId, Long cursor, Integer size) {
        int pageSize = pageSize(size);
        LocalDateTime since = visibleSince();
        Limit limit = Limit.of(pageSize + 1);
        List<Notification> found = cursor == null
            ? notificationRepository.findByUserIdAndCreatedAtGreaterThanEqualOrderByIdDesc(userId, since, limit)
            : notificationRepository.findByUserIdAndIdLessThanAndCreatedAtGreaterThanEqualOrderByIdDesc(
                userId, cursor, since, limit);
        boolean hasNext = found.size() > pageSize;
        List<NotificationResponse> page = found.stream()
            .limit(pageSize)
            .map(NotificationResponse::from)
            .toList();
        return NotificationPageResponse.of(page, hasNext);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        return UnreadCountResponse.from(
            notificationRepository.countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(userId, visibleSince()));
    }

    /**
     * 내 알림이 아니면 존재 여부가 드러나지 않도록 NOTIFICATION_NOT_FOUND로 응답한다. 이미 읽었으면 그대로 둔다.
     */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now(clock));
    }

    /**
     * 테스트 알림을 저장하고 바로 푸시한다. 푸시는 외부 호출이라 저장 트랜잭션이 끝난 뒤에 보낸다.
     */
    public TestPushResponse sendTest(Long userId) {
        if (!deviceTokenService.hasDevice(userId)) {
            throw new BusinessException(NotificationErrorCode.NO_REGISTERED_DEVICE);
        }
        Long notificationId = notificationSendService.createTest(userId, TEST_TITLE, TEST_BODY);
        return TestPushResponse.from(pushDispatcher.dispatch(notificationId));
    }

    /**
     * @return 지운 알림 수
     */
    @Transactional
    public int deleteExpired() {
        return notificationRepository.deleteCreatedBefore(visibleSince());
    }

    private LocalDateTime visibleSince() {
        return LocalDateTime.now(clock).minus(RETENTION);
    }

    private int pageSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
