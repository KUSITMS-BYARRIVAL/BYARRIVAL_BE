package com.byarrival.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DiscordWebhookClientTest {

    @Test
    void 디스코드_글자_수_제한을_넘는_메시지는_잘라서_보낸다() {
        String content = DiscordWebhookClient.truncate("a".repeat(3000));

        assertThat(content).hasSize(DiscordWebhookClient.MAX_CONTENT_LENGTH).endsWith("…(생략)");
    }

    @Test
    void 제한_이내_메시지는_그대로_보낸다() {
        assertThat(DiscordWebhookClient.truncate("배치 완료")).isEqualTo("배치 완료");
    }

    @Test
    void 웹훅_URL이_없으면_아무것도_하지_않는다() throws InterruptedException {
        DiscordWebhookClient client = new DiscordWebhookClient(new MonitoringProperties("test",
            new MonitoringProperties.Discord("", "", Duration.ofMinutes(5), Duration.ofSeconds(1),
                Duration.ofSeconds(1))));

        assertThatCode(() -> client.send("", "내용")).doesNotThrowAnyException();
        client.destroy();
    }
}
