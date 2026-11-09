package com.byarrival.domain.notification.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 푸시 발송 설정. fcmEnabled가 false면 FCM 대신 로그만 남긴다.
 */
@ConfigurationProperties(prefix = "notification.push")
public record PushProperties(
    boolean fcmEnabled,
    String fcmCredentialsBase64,
    String androidChannelId
) {
}
