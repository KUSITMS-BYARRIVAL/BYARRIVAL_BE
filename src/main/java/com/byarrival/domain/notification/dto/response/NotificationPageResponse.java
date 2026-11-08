package com.byarrival.domain.notification.dto.response;

import java.util.List;

/**
 * 알림함 한 페이지. 다음 페이지는 nextCursor를 cursor로 넘겨 요청한다. 마지막 페이지면 nextCursor는 null이다.
 */
public record NotificationPageResponse(
    List<NotificationResponse> notifications,
    Long nextCursor,
    boolean hasNext
) {

    public static NotificationPageResponse of(List<NotificationResponse> notifications, boolean hasNext) {
        Long nextCursor = hasNext ? notifications.getLast().id() : null;
        return new NotificationPageResponse(notifications, nextCursor, hasNext);
    }
}
