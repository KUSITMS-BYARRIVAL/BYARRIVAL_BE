package com.byarrival.domain.notification.service;

import com.byarrival.common.time.ServiceDay;
import com.byarrival.domain.notification.dto.NotificationRequest;
import com.byarrival.domain.notification.entity.Notification;
import com.byarrival.domain.notification.entity.NotificationType;
import com.byarrival.domain.notification.event.NotificationCreatedEvent;
import com.byarrival.domain.notification.repository.NotificationRepository;
import com.byarrival.domain.user.entity.NotificationPermission;
import com.byarrival.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 기능이 알림을 보내는 입구. 발송 정책을 확인하고 알림함에 저장한 뒤, 푸시는 커밋 후 비동기로 보낸다.
 * <ul>
 *     <li>행동 유도 알림은 서비스일마다 유형별 1번, 합쳐서 하루 2번까지만 보낸다.</li>
 *     <li>OS 알림 권한이 거부됐거나 수신 설정이 꺼져 있으면 운영 안내까지 알림함에만 저장하고 푸시는 보내지 않는다.</li>
 *     <li>운영 안내는 횟수 제한 없이 보낸다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSendService {

    static final int DAILY_ACTION_LIMIT = 2;

    private final NotificationRepository notificationRepository;
    private final NotificationSettingService settingService;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * @return 저장한 알림 id. 발송 제한에 걸려 보내지 않았으면 비어 있다.
     */
    @Transactional
    public Optional<Long> send(NotificationRequest request) {
        NotificationType type = request.type();
        if (type == NotificationType.TEST) {
            throw new IllegalStateException("테스트 알림은 테스트 발송 API로만 보낸다.");
        }

        Long userId = request.userId();
        LocalDate serviceDate = ServiceDay.today(clock);
        if (type.isAction() && !withinDailyLimits(userId, type, serviceDate)) {
            log.info("알림 발송 제한으로 건너뜀: userId={}, type={}", userId, type);
            return Optional.empty();
        }

        boolean push = canPush(userId, type);
        Notification notification = notificationRepository.save(Notification.create(
            userId, type, request.title(), request.body(), request.params(), serviceDate, push));
        if (push) {
            eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getId()));
        }
        return Optional.of(notification.getId());
    }

    /**
     * 테스트 알림을 저장한다. 수신 설정·권한·횟수 제한과 상관없이 바로 푸시할 수 있게 저장만 한다.
     */
    @Transactional
    public Long createTest(Long userId, String title, String body) {
        Notification notification = notificationRepository.save(Notification.create(
            userId, NotificationType.TEST, title, body, Map.of(), ServiceDay.today(clock), true));
        return notification.getId();
    }

    private boolean withinDailyLimits(Long userId, NotificationType type, LocalDate serviceDate) {
        if (notificationRepository.existsByUserIdAndTypeAndServiceDate(userId, type, serviceDate)) {
            return false;
        }
        long sentToday = notificationRepository.countByUserIdAndServiceDateAndTypeIn(
            userId, serviceDate, NotificationType.actionTypes());
        return sentToday < DAILY_ACTION_LIMIT;
    }

    private boolean canPush(Long userId, NotificationType type) {
        Optional<NotificationPermission> permission = userService.findNotificationPermission(userId);
        if (permission.filter(NotificationPermission.DENIED::equals).isPresent()) {
            return false;
        }
        return settingService.isEnabled(userId, type.getCategory());
    }
}
