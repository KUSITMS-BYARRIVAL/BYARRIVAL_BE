package com.byarrival.common.monitoring;

import com.byarrival.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 5xx 에러를 디스코드 서버 에러 채널에 알린다. 같은 에러(예외 종류·메서드·URI)는 정해진 시간 동안 한 번만 보내고,
 * 그사이 생략한 횟수는 다음 알림에 붙인다.
 */
@Slf4j
@Component
public class ServerErrorNotifier {

    static final int MAX_MESSAGE_LENGTH = 500;
    private static final int CLEANUP_THRESHOLD = 1_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DiscordWebhookClient discordWebhookClient;
    private final MonitoringProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ServerErrorNotifier(DiscordWebhookClient discordWebhookClient, MonitoringProperties properties,
        Clock clock) {
        this.discordWebhookClient = discordWebhookClient;
        this.properties = properties;
        this.clock = clock;
    }

    // 알림이 실패해도 원래 에러 응답은 나가야 하므로 예외를 밖으로 던지지 않는다
    public void notify(String method, String uri, Throwable error) {
        if (!properties.discord().errorEnabled()) {
            return;
        }
        try {
            notifyOnce(method, uri, error);
        } catch (RuntimeException e) {
            log.warn("[ServerErrorNotifier] 서버 에러 알림 실패 - {}", e.getMessage(), e);
        }
    }

    private void notifyOnce(String method, String uri, Throwable error) {
        Instant now = clock.instant();
        Duration dedupWindow = properties.discord().errorDedupWindow();
        String errorName = errorName(error);
        String key = errorName + " " + method + " " + uri;

        // 새 구간을 열 때만 알린다. 열린 구간 안이면 생략 횟수만 센다
        long[] suppressedBefore = {-1};
        windows.compute(key, (k, window) -> {
            if (window == null || window.isExpired(now, dedupWindow)) {
                suppressedBefore[0] = window == null ? 0 : window.suppressed();
                return new Window(now, 0);
            }
            return window.suppressOne();
        });
        cleanUpIfLarge(now, dedupWindow);
        if (suppressedBefore[0] < 0) {
            return;
        }
        discordWebhookClient.send(properties.discord().errorWebhookUrl(),
            format(method, uri, errorName, error.getMessage(), suppressedBefore[0], dedupWindow));
    }

    private String format(String method, String uri, String errorName, String message, long suppressed,
        Duration dedupWindow) {
        StringBuilder content = new StringBuilder()
            .append("🚨 **서버 에러** `").append(properties.instanceName()).append("`\n")
            .append('`').append(method).append(' ').append(sanitize(uri)).append("`\n")
            .append('`').append(errorName).append('`');
        if (message != null && !message.isBlank()) {
            content.append(": ").append(sanitize(abbreviate(message)));
        }
        content.append('\n').append(LocalDateTime.now(clock).format(TIME_FORMAT)).append(" KST");
        if (suppressed > 0) {
            content.append(" · 직전 ").append(dedupWindow.toMinutes()).append("분 동안 같은 에러 ")
                .append(suppressed).append("건 생략");
        }
        return content.toString();
    }

    private static String errorName(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return error.getClass().getSimpleName() + "(" + businessException.getErrorCode() + ")";
        }
        return error.getClass().getName();
    }

    private static String abbreviate(String message) {
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_MESSAGE_LENGTH) + "…";
    }

    // 코드 블록이 깨지지 않게 백틱을 바꾼다
    private static String sanitize(String text) {
        return text.replace('`', '\'');
    }

    private void cleanUpIfLarge(Instant now, Duration dedupWindow) {
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now, dedupWindow));
        }
    }

    private record Window(Instant startedAt, long suppressed) {

        boolean isExpired(Instant now, Duration dedupWindow) {
            return !now.isBefore(startedAt.plus(dedupWindow));
        }

        Window suppressOne() {
            return new Window(startedAt, suppressed + 1);
        }
    }
}
