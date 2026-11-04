package com.byarrival.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.byarrival.common.time.ServiceDay;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void 서버_기본_시간대가_UTC여도_Clock은_KST를_따른다() {
        Clock clock = new ClockConfig().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void 서버_기본_시간대가_UTC여도_서비스일은_KST_04시_기준으로_바뀐다() {
        ZoneId zone = new ClockConfig().clock().getZone();
        // UTC 19:00 = KST 다음 날 04:00
        Clock beforeFour = Clock.fixed(Instant.parse("2026-11-02T18:59:59Z"), zone);
        Clock atFour = Clock.fixed(Instant.parse("2026-11-02T19:00:00Z"), zone);

        assertThat(ServiceDay.today(beforeFour)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(ServiceDay.today(atFour)).isEqualTo(LocalDate.of(2026, 11, 3));
    }
}
