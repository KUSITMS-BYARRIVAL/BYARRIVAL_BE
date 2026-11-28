package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.notification.entity.BroadcastKind;
import com.byarrival.domain.notification.entity.BroadcastTarget;
import com.byarrival.domain.notification.entity.NotificationBroadcast.BroadcastContent;
import com.byarrival.domain.notification.entity.NotificationScreen;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 어드민 알림 작성·수정(NOTI-01).
 *
 * @param kind        공지(NOTICE) 또는 이벤트(EVENT). 이벤트는 제목 앞에 (광고)가 자동으로 붙는다
 * @param target      전체·직장인·학생·기타
 * @param title       최대 20자. (광고)는 넣지 않는다
 * @param body        최대 60자
 * @param screen      푸시를 눌렀을 때 열 화면
 * @param url         공지사항(NOTICE) 화면일 때만 https 주소
 * @param scheduledAt 예약 시각(분 단위, 초는 버린다). 없으면 즉시 발송
 */
public record AdminNotificationRequest(
    @NotNull BroadcastKind kind,
    @NotNull BroadcastTarget target,
    @NotBlank @Size(max = 20) String title,
    @NotBlank @Size(max = 60) String body,
    @NotNull NotificationScreen screen,
    @Size(max = 500) String url,
    LocalDateTime scheduledAt
) {

    public BroadcastContent content() {
        return new BroadcastContent(kind, target, title.strip(), body.strip(), screen,
            url == null || url.isBlank() ? null : url.strip());
    }
}
