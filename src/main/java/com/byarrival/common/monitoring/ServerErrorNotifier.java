package com.byarrival.common.monitoring;

import com.byarrival.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 5xx 에러를 디스코드 서버 에러 채널에 알린다. 같은 에러(예외 종류·메서드·URI)는 정해진 시간 동안 한 번만 보내고,
 * 그사이 생략한 횟수는 다음 알림에 붙인다.
 * 예외 메시지에는 사용자 값이 섞일 수 있으므로 보내지 않는다. 원인은 서버 로그에서 확인한다.
 */
@Slf4j
@Component
public class ServerErrorNotifier {

    static final int MAX_KEYS = 1_000;
    static final String OVERFLOW_KEY = "__overflow__";
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DiscordWebhookClient discordWebhookClient;
    private final MonitoringProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastCleanup = new AtomicReference<>(Instant.EPOCH);

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
            log.warn("[ServerErrorNotifier] 서버 에러 알림 실패 - {}", e.getClass().getSimpleName());
        }
    }

    private void notifyOnce(String method, String uri, Throwable error) {
        Instant now = clock.instant();
        Duration dedupWindow = properties.discord().errorDedupWindow();
        String errorName = errorName(error);
        cleanUpIfLarge(now, dedupWindow);
        String key = keyOf(errorName + " " + method + " " + uri);

        // 새 구간을 열 때만 알린다. 열린 구간 안이면 생략 횟수만 센다
        long[] suppressedBefore = {-1};
        Window opened = windows.compute(key, (k, window) -> {
            if (window == null || window.isExpired(now, dedupWindow)) {
                suppressedBefore[0] = window == null ? 0 : window.suppressed();
                return new Window(now, 0);
            }
            return window.suppressOne();
        });
        if (suppressedBefore[0] < 0) {
            return;
        }
        String content = format(method, uri, errorName, suppressedBefore[0], dedupWindow,
            OVERFLOW_KEY.equals(key));
        discordWebhookClient.send(properties.discord().errorWebhookUrl(), content)
            .thenAccept(sent -> {
                if (!sent) {
                    release(key, opened);
                }
            });
    }

    // 상한을 넘으면 새 에러는 하나의 키로 묶어 알림이 폭주하지 않게 한다
    private String keyOf(String key) {
        if (windows.containsKey(key) || windows.size() < MAX_KEYS) {
            return key;
        }
        return OVERFLOW_KEY;
    }

    // 전송에 실패하면 구간을 닫아 다음 같은 에러를 바로 알리고, 이번 건은 생략 건수에 넣는다
    private void release(String key, Window opened) {
        windows.computeIfPresent(key, (k, window) -> window.startedAt().equals(opened.startedAt())
            ? new Window(Instant.EPOCH, window.suppressed() + 1)
            : window);
    }

    private String format(String method, String uri, String errorName, long suppressed, Duration dedupWindow,
        boolean overflow) {
        StringBuilder content = new StringBuilder()
            .append("🚨 **서버 에러** `").append(properties.instanceName()).append("`\n")
            .append('`').append(method).append(' ').append(sanitize(uri)).append("`\n")
            .append('`').append(errorName).append('`')
            .append('\n').append(LocalDateTime.now(clock).format(TIME_FORMAT)).append(" KST");
        if (suppressed > 0) {
            content.append(" · 직전 ").append(dedupWindow.toMinutes()).append("분 동안 같은 에러 ")
                .append(suppressed).append("건 생략");
        }
        if (overflow) {
            content.append("\n서로 다른 에러가 너무 많아 이후 새 에러는 이 알림 하나로 묶습니다. 서버 로그를 확인하세요.");
        }
        return content.toString();
    }

    private static String errorName(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return error.getClass().getSimpleName() + "(" + businessException.getErrorCode() + ")";
        }
        return error.getClass().getName();
    }

    // 코드 블록이 깨지지 않게 백틱을 바꾼다
    private static String sanitize(String text) {
        return text.replace('`', '\'');
    }

    // 장애 중 매 요청이 맵 전체를 돌지 않도록 정리는 1분에 한 스레드만 한다.
    // 생략 건수가 남은 구간은 다음 알림에 붙여야 하므로 지우지 않는다.
    private void cleanUpIfLarge(Instant now, Duration dedupWindow) {
        Instant last = lastCleanup.get();
        if (windows.size() < MAX_KEYS || now.isBefore(last.plus(CLEANUP_INTERVAL))
            || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now, dedupWindow)
            && entry.getValue().suppressed() == 0);
    }

    int windowCount() {
        return windows.size();
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
