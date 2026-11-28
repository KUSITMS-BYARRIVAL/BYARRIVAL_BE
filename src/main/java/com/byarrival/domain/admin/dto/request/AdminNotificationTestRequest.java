package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import com.byarrival.domain.notification.entity.NotificationBroadcast.BroadcastContent;
import com.byarrival.domain.notification.entity.NotificationScreen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 테스트 발송(NOTI-02). 작성 중인 값을 그대로 보낸다. 대상과 예약 시각은 쓰지 않는다.
 */
public record AdminNotificationTestRequest(
    @NotNull BroadcastKind kind,
    @NotBlank @Size(max = 20) String title,
    @NotBlank @Size(max = 60) String body,
    @NotNull NotificationScreen screen,
    @Size(max = 500) String url
) {

    public BroadcastContent content() {
        return new BroadcastContent(kind, BroadcastTarget.ALL, title.strip(), body.strip(), screen,
            url == null || url.isBlank() ? null : url.strip());
    }
}
