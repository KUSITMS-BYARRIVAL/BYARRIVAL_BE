package com.byarrival.common.monitoring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영 알림 설정. 웹훅 URL이 비어 있으면 그 채널 알림은 보내지 않는다.
 *
 * @param instanceName 알림에 표시할 서버 이름(blue/green 컨테이너 구분)
 * @param discord      디스코드 웹훅 설정
 */
@ConfigurationProperties(prefix = "monitoring")
public record MonitoringProperties(String instanceName, Discord discord) {

    public MonitoringProperties {
        if (instanceName == null || instanceName.isBlank()) {
            instanceName = "unknown";
        }
        if (discord == null) {
            throw new IllegalArgumentException("monitoring.discord 설정이 필요합니다.");
        }
    }

    /**
     * @param errorWebhookUrl  서버 에러(5xx) 채널 웹훅
     * @param batchWebhookUrl  배치·운영 채널 웹훅
     * @param errorDedupWindow 같은 에러(예외 종류·메서드·URI)는 이 시간 동안 한 번만 알린다
     * @param connectTimeout   웹훅 연결 제한 시간
     * @param readTimeout      웹훅 응답 제한 시간
     */
    public record Discord(
        String errorWebhookUrl,
        String batchWebhookUrl,
        Duration errorDedupWindow,
        Duration connectTimeout,
        Duration readTimeout
    ) {

        public Discord {
            requireHttpsOrBlank(errorWebhookUrl, "monitoring.discord.error-webhook-url");
            requireHttpsOrBlank(batchWebhookUrl, "monitoring.discord.batch-webhook-url");
            requirePositive(errorDedupWindow, "monitoring.discord.error-dedup-window");
            requirePositive(connectTimeout, "monitoring.discord.connect-timeout");
            requirePositive(readTimeout, "monitoring.discord.read-timeout");
        }

        public boolean errorEnabled() {
            return hasText(errorWebhookUrl);
        }

        public boolean batchEnabled() {
            return hasText(batchWebhookUrl);
        }

        private static void requireHttpsOrBlank(String url, String name) {
            if (hasText(url) && !url.startsWith("https://")) {
                throw new IllegalArgumentException(name + "는 https 주소여야 합니다.");
            }
        }

        private static void requirePositive(Duration duration, String name) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException(name + "는 0보다 커야 합니다.");
            }
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
