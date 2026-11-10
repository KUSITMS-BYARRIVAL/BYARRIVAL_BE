package com.byarrival.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ServiceDayTest {

    @Test
    void 새벽_4시부터_새_서비스일이다() {
        assertThat(ServiceDay.of(LocalDateTime.of(2026, 11, 2, 4, 0))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void 새벽_3시_59분은_전날_서비스일이다() {
        assertThat(ServiceDay.of(LocalDateTime.of(2026, 11, 2, 3, 59, 59))).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void 자정이_지나도_04시_전이면_서비스일이_바뀌지_않는다() {
        assertThat(ServiceDay.of(LocalDateTime.of(2026, 11, 3, 0, 30))).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void Clock_기준으로_오늘_서비스일을_구한다() {
        ZoneId kst = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 2, 23, 45).atZone(kst).toInstant(), kst);

        assertThat(ServiceDay.today(clock)).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void 서비스일은_다음_날_04시에_끝난다() {
        assertThat(ServiceDay.endOf(LocalDate.of(2026, 11, 2))).isEqualTo(LocalDateTime.of(2026, 11, 3, 4, 0));
    }
}
