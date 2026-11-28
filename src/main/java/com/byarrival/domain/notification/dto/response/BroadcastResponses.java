package com.byarrival.domain.notification.dto.response;

import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastStatus;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import com.byarrival.domain.notification.entity.NotificationBroadcast;
import com.byarrival.domain.notification.entity.NotificationScreen;
import com.byarrival.domain.notification.entity.NotificationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 알림 발송 API 응답 모음(NOTI-01·02).
 */
public final class BroadcastResponses {

    private BroadcastResponses() {
    }

    /**
     * @param title          운영자가 쓴 제목
     * @param displayTitle   사용자에게 보이는 제목. 이벤트는 앞에 (광고)가 붙는다
     * @param recipientCount 실제로 보낸 회원 수. 발송을 시작하기 전이면 null
     * @param successCount   푸시를 받은 회원 수
     * @param failureCount   기기가 없거나 푸시를 보내지 못한 회원 수
     * @param editable       수정·취소할 수 있는지(발송 시작 전)
     */
    public record Broadcast(Long broadcastId, BroadcastKind kind, BroadcastTarget target, String title,
                            String displayTitle, String body, NotificationScreen screen, String url,
                            LocalDateTime scheduledAt, BroadcastStatus status, Integer recipientCount,
                            int successCount, int failureCount, LocalDateTime startedAt, LocalDateTime completedAt,
                            boolean editable) {

        public static Broadcast of(NotificationBroadcast broadcast, LocalDateTime now) {
            return new Broadcast(broadcast.getId(), broadcast.getKind(), broadcast.getTarget(), broadcast.getTitle(),
                broadcast.getKind().displayTitle(broadcast.getTitle()), broadcast.getBody(), broadcast.getScreen(),
                broadcast.getUrl(), broadcast.getScheduledAt(), broadcast.getStatus(), broadcast.getRecipientCount(),
                broadcast.getSuccessCount(), broadcast.getFailureCount(), broadcast.getStartedAt(),
                broadcast.getCompletedAt(), broadcast.isEditable(now));
        }
    }

    /**
     * @param count 지금 조건으로 받을 회원 수(수신 설정을 끈 사람·미동의자 제외)
     * @param night 야간(21~08시) 발송이라 이벤트는 야간 광고 동의자에게만 가는지
     */
    public record Estimate(long count, boolean night) {
    }

    /**
     * @param manual 운영자가 보낸 알림. 예약 시각 최근 순
     * @param auto   자동 알림. 서비스일·유형별 건수
     */
    public record History(LocalDate from, LocalDate to, List<Broadcast> manual, List<AutoHistory> auto) {
    }

    /**
     * @param pending 아직 푸시 결과가 나오지 않은 수
     */
    public record AutoHistory(LocalDate serviceDate, NotificationType type, long successCount, long failureCount,
                              long pendingCount) {
    }

    /**
     * @param results 테스트 대상 회원마다 푸시를 받은 기기 수
     */
    public record TestSend(List<TestResult> results) {
    }

    public record TestResult(Long userId, int deliveredDevices) {
    }
}
