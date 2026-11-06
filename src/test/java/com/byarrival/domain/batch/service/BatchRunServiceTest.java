package com.byarrival.domain.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byarrival.domain.batch.entity.BatchRun;
import com.byarrival.domain.batch.entity.BatchRunStatus;
import com.byarrival.domain.batch.repository.BatchRunRepository;
import com.byarrival.domain.feed.entity.RssFeed;
import com.byarrival.domain.feed.repository.RssFeedRepository;
import com.byarrival.domain.feed.service.FeedService;
import com.byarrival.support.TestcontainersConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@Import(TestcontainersConfig.class)
class BatchRunServiceTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 3);

    @Autowired
    private BatchRunService batchRunService;

    @Autowired
    private BatchRunRepository batchRunRepository;

    @Autowired
    private FeedService feedService;

    @Autowired
    private RssFeedRepository rssFeedRepository;

    @AfterEach
    void tearDown() {
        batchRunRepository.deleteAll();
        rssFeedRepository.deleteAll();
    }

    @Test
    void 배치를_시작하면_실행_중으로_기록된다() {
        Long batchRunId = batchRunService.start(SERVICE_DATE).orElseThrow();

        BatchRun batchRun = batchRunRepository.findById(batchRunId).orElseThrow();
        assertThat(batchRun.getServiceDate()).isEqualTo(SERVICE_DATE);
        assertThat(batchRun.getStatus()).isEqualTo(BatchRunStatus.RUNNING);
        assertThat(batchRun.getStartedAt()).isNotNull();
    }

    @Test
    void 실행_중인_서비스일_배치는_다시_시작하지_않는다() {
        batchRunService.start(SERVICE_DATE);

        assertThat(batchRunService.start(SERVICE_DATE)).isEmpty();
    }

    @Test
    void 완료된_서비스일_배치는_다시_시작하지_않는다() {
        Long batchRunId = batchRunService.start(SERVICE_DATE).orElseThrow();
        batchRunService.complete(batchRunId);

        assertThat(batchRunService.start(SERVICE_DATE)).isEmpty();
        BatchRun batchRun = batchRunRepository.findById(batchRunId).orElseThrow();
        assertThat(batchRun.getStatus()).isEqualTo(BatchRunStatus.COMPLETED);
        assertThat(batchRun.getFinishedAt()).isNotNull();
    }

    @Test
    void 실패한_서비스일_배치는_다시_시작할_수_있다() {
        Long failedId = batchRunService.start(SERVICE_DATE).orElseThrow();
        batchRunService.fail(failedId, "DB 연결 실패");

        Optional<Long> retriedId = batchRunService.start(SERVICE_DATE);

        assertThat(retriedId).isPresent().get().isNotEqualTo(failedId);
        BatchRun failed = batchRunRepository.findById(failedId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(BatchRunStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("DB 연결 실패");
    }

    @Test
    void 동시에_시작해도_같은_서비스일_배치는_하나만_시작된다() throws Exception {
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Optional<Long>>> tasks = IntStream.range(0, threads)
                .<Callable<Optional<Long>>>mapToObj(i -> () -> {
                    ready.countDown();
                    go.await();
                    return batchRunService.start(SERVICE_DATE);
                })
                .toList();
            List<Future<Optional<Long>>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            go.countDown();

            long started = 0;
            for (Future<Optional<Long>> future : futures) {
                if (future.get().isPresent()) {
                    started++;
                }
            }

            assertThat(started).isEqualTo(1);
            assertThat(batchRunRepository.findAll())
                .filteredOn(run -> run.getServiceDate().equals(SERVICE_DATE))
                .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 같은_서비스일에_실행_중인_배치는_DB에_두_개_저장할_수_없다() {
        batchRunRepository.saveAndFlush(BatchRun.start(SERVICE_DATE, LocalDateTime.of(2026, 11, 2, 23, 45)));

        assertThatThrownBy(() -> batchRunRepository.saveAndFlush(
            BatchRun.start(SERVICE_DATE, LocalDateTime.of(2026, 11, 2, 23, 45))))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 다른_서비스일_배치는_따로_시작한다() {
        batchRunService.start(SERVICE_DATE);

        assertThat(batchRunService.start(SERVICE_DATE.plusDays(1))).isPresent();
    }

    @Test
    void 수집한_Feed만_마지막_수집_시각이_갱신된다() {
        RssFeed fetched = rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/a.xml", null));
        RssFeed notFetched = rssFeedRepository.save(RssFeed.create("한국경제", "https://news.example.com/b.xml", null));
        LocalDateTime fetchedAt = LocalDateTime.of(2026, 11, 2, 23, 46);

        feedService.markFetched(List.of(fetched.getId()), fetchedAt);

        assertThat(rssFeedRepository.findById(fetched.getId()).orElseThrow().getLastFetchedAt()).isEqualTo(fetchedAt);
        assertThat(rssFeedRepository.findById(notFetched.getId()).orElseThrow().getLastFetchedAt()).isNull();
    }
}
