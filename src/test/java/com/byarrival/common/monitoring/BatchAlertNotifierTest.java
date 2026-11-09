package com.byarrival.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchAlertNotifierTest {

    private static final String WEBHOOK = "https://discord.com/api/webhooks/1/batch";

    private final DiscordWebhookClient discordWebhookClient = mock(DiscordWebhookClient.class);

    @Test
    void 배치_채널로_보내고_서버_이름을_붙인다() {
        notifier(WEBHOOK).send("✅ 일일 배치 완료");

        verify(discordWebhookClient).send(WEBHOOK, "✅ 일일 배치 완료\n`byarrival-app-green`");
    }

    @Test
    void 웹훅이_없으면_보내지_않는다() {
        notifier("").send("✅ 일일 배치 완료");

        verify(discordWebhookClient, never()).send(anyString(), anyString());
    }

    @Test
    void 본문이_길어도_서버_이름은_잘리지_않는다() {
        notifier(WEBHOOK).send("feedIds=" + "1, ".repeat(1000));

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(discordWebhookClient).send(eq(WEBHOOK), content.capture());
        assertThat(content.getValue())
            .hasSizeLessThanOrEqualTo(DiscordWebhookClient.MAX_CONTENT_LENGTH)
            .endsWith("\n`byarrival-app-green`");
    }

    private BatchAlertNotifier notifier(String batchWebhookUrl) {
        MonitoringProperties properties = new MonitoringProperties("byarrival-app-green",
            new MonitoringProperties.Discord("", batchWebhookUrl, Duration.ofMinutes(5), Duration.ofSeconds(3),
                Duration.ofSeconds(5)));
        return new BatchAlertNotifier(discordWebhookClient, properties);
    }
}
