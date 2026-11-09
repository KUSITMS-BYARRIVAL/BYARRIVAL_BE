package com.byarrival.common.monitoring;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;

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

    private BatchAlertNotifier notifier(String batchWebhookUrl) {
        MonitoringProperties properties = new MonitoringProperties("byarrival-app-green",
            new MonitoringProperties.Discord("", batchWebhookUrl, Duration.ofMinutes(5), Duration.ofSeconds(3),
                Duration.ofSeconds(5)));
        return new BatchAlertNotifier(discordWebhookClient, properties);
    }
}
