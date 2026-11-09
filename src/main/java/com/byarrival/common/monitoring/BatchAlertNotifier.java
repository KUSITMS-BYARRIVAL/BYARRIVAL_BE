package com.byarrival.common.monitoring;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 배치·운영 알림을 디스코드 배치 채널에 보낸다. 메시지 끝에 어느 서버에서 보냈는지 붙인다.
 */
@Component
@RequiredArgsConstructor
public class BatchAlertNotifier {

    private final DiscordWebhookClient discordWebhookClient;
    private final MonitoringProperties properties;

    public void send(String content) {
        if (!properties.discord().batchEnabled()) {
            return;
        }
        discordWebhookClient.send(properties.discord().batchWebhookUrl(),
            content + "\n`" + properties.instanceName() + "`");
    }
}
