package com.byarrival.domain.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class DailyBatchSchedulerTest {

    @Test
    void 일일_배치는_서버_시간대와_상관없이_KST_23시_45분에_시작한다() throws NoSuchMethodException {
        Scheduled scheduled = DailyBatchScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 45 23 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
