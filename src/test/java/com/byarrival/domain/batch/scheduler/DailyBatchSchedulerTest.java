package com.byarrival.domain.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.batch.service.BatchMonitor;
import com.byarrival.domain.batch.service.DailyBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class DailyBatchSchedulerTest {

    @Test
    void 일일_배치는_서버_시간대와_상관없이_KST_23시_45분에_시작한다() throws NoSuchMethodException {
        Scheduled scheduled = DailyBatchScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 45 23 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 실패한_배치는_마감_전_23시_52분과_56분에_다시_실행한다() throws NoSuchMethodException {
        Scheduled scheduled = DailyBatchScheduler.class.getMethod("retry").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 52,56 23 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }

    @Test
    void 재시도도_같은_서비스일_배치를_실행하고_예외는_비정상_종료로_알린다() {
        DailyBatchService dailyBatchService = mock(DailyBatchService.class);
        BatchMonitor batchMonitor = mock(BatchMonitor.class);
        IllegalStateException error = new IllegalStateException("DB 연결 실패");
        willThrow(error).given(dailyBatchService).run();

        assertThatCode(() -> new DailyBatchScheduler(dailyBatchService, batchMonitor).retry())
            .doesNotThrowAnyException();
        verify(dailyBatchService).run();
        verify(batchMonitor).crashed(error);
    }

    @Test
    void 배치가_예외로_중단되면_비정상_종료를_알린다() {
        DailyBatchService dailyBatchService = mock(DailyBatchService.class);
        BatchMonitor batchMonitor = mock(BatchMonitor.class);
        IllegalStateException error = new IllegalStateException("DB 연결 실패");
        willThrow(error).given(dailyBatchService).run();

        assertThatCode(() -> new DailyBatchScheduler(dailyBatchService, batchMonitor).run())
            .doesNotThrowAnyException();
        verify(batchMonitor).crashed(error);
    }
}
