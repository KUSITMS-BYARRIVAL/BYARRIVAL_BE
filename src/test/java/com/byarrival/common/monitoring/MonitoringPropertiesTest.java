package com.byarrival.common.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MonitoringPropertiesTest {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Test
    void 웹훅_URL이_비어_있으면_알림을_끈다() {
        MonitoringProperties.Discord discord = new MonitoringProperties.Discord("", null, WINDOW, TIMEOUT, TIMEOUT);

        assertThat(discord.errorEnabled()).isFalse();
        assertThat(discord.batchEnabled()).isFalse();
    }

    @Test
    void 웹훅_URL은_https만_허용한다() {
        assertThatThrownBy(() -> new MonitoringProperties.Discord("http://discord.com/api/webhooks/1/a", "",
            WINDOW, TIMEOUT, TIMEOUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error-webhook-url");
    }

    @Test
    void 중복_억제_시간은_0보다_커야_한다() {
        assertThatThrownBy(() -> new MonitoringProperties.Discord("", "", Duration.ZERO, TIMEOUT, TIMEOUT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("error-dedup-window");
    }

    @Test
    void 서버_이름이_없으면_unknown으로_표시한다() {
        MonitoringProperties properties = new MonitoringProperties(" ",
            new MonitoringProperties.Discord("", "", WINDOW, TIMEOUT, TIMEOUT));

        assertThat(properties.instanceName()).isEqualTo("unknown");
    }
}
