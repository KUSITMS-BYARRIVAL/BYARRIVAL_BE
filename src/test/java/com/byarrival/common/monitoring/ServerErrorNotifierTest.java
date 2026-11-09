package com.byarrival.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.byarrival.common.exception.BusinessException;
import com.byarrival.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ServerErrorNotifierTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String WEBHOOK = "https://discord.com/api/webhooks/1/error";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 8, 30);

    private DiscordWebhookClient discordWebhookClient;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        discordWebhookClient = mock(DiscordWebhookClient.class);
        clock = new MutableClock(NOW.atZone(KST).toInstant());
    }

    @Test
    void 서버_에러를_요청_정보와_함께_알린다() {
        notifier(WEBHOOK).notify("GET", "/api/v1/users/me", new IllegalStateException("DB 연결 실패"));

        String content = sentContents().getFirst();
        assertThat(content)
            .contains("서버 에러", "byarrival-app-blue", "GET /api/v1/users/me",
                "java.lang.IllegalStateException", "DB 연결 실패", "2026-11-09 08:30:00 KST");
    }

    @Test
    void 같은_에러는_정해진_시간_동안_한_번만_알린다() {
        ServerErrorNotifier notifier = notifier(WEBHOOK);

        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("1"));
        clock.plus(Duration.ofMinutes(4));
        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("2"));
        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("3"));

        verify(discordWebhookClient, times(1)).send(anyString(), anyString());
    }

    @Test
    void 시간이_지나면_다시_알리고_그사이_생략한_횟수를_붙인다() {
        ServerErrorNotifier notifier = notifier(WEBHOOK);

        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("1"));
        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("2"));
        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("3"));
        clock.plus(Duration.ofMinutes(5));
        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("4"));

        List<String> contents = sentContents();
        assertThat(contents).hasSize(2);
        assertThat(contents.get(1)).contains("직전 5분 동안 같은 에러 2건 생략");
    }

    @Test
    void URI나_예외_종류가_다르면_따로_알린다() {
        ServerErrorNotifier notifier = notifier(WEBHOOK);

        notifier.notify("GET", "/api/v1/users/me", new IllegalStateException("1"));
        notifier.notify("GET", "/api/v1/terms", new IllegalStateException("1"));
        notifier.notify("GET", "/api/v1/users/me", new NullPointerException("1"));

        verify(discordWebhookClient, times(3)).send(anyString(), anyString());
    }

    @Test
    void BusinessException은_에러_코드를_함께_표시한다() {
        notifier(WEBHOOK).notify("POST", "/api/v1/auth/login",
            new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR));

        assertThat(sentContents().getFirst()).contains("BusinessException(INTERNAL_SERVER_ERROR)");
    }

    @Test
    void 긴_예외_메시지는_잘라서_보낸다() {
        notifier(WEBHOOK).notify("GET", "/api/test", new IllegalStateException("가".repeat(800)));

        assertThat(sentContents().getFirst())
            .contains("가".repeat(ServerErrorNotifier.MAX_MESSAGE_LENGTH) + "…")
            .doesNotContain("가".repeat(ServerErrorNotifier.MAX_MESSAGE_LENGTH + 1));
    }

    @Test
    void 웹훅이_없으면_알리지_않는다() {
        notifier("").notify("GET", "/api/test", new IllegalStateException("x"));

        verify(discordWebhookClient, never()).send(anyString(), anyString());
    }

    private ServerErrorNotifier notifier(String webhookUrl) {
        MonitoringProperties properties = new MonitoringProperties("byarrival-app-blue",
            new MonitoringProperties.Discord(webhookUrl, "", Duration.ofMinutes(5), Duration.ofSeconds(3),
                Duration.ofSeconds(5)));
        return new ServerErrorNotifier(discordWebhookClient, properties, clock);
    }

    private List<String> sentContents() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(discordWebhookClient, atLeastOnce()).send(eq(WEBHOOK), content.capture());
        return content.getAllValues();
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void plus(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
