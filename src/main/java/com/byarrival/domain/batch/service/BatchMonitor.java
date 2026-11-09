package com.byarrival.domain.batch.service;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 일일 배치 결과를 메트릭으로 남기고 디스코드 배치 채널에 알린다.
 */
@Component
public class BatchMonitor {

    static final String DURATION_METRIC = "byarrival.batch.duration";
    static final String LAST_SUCCESS_METRIC = "byarrival.batch.last.success";
    static final String CATEGORY_ARTICLES_METRIC = "byarrival.batch.category.articles";

    private final MeterRegistry meterRegistry;
    private final BatchAlertNotifier batchAlertNotifier;
    private final Clock clock;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final Map<Long, AtomicLong> categoryArticles = new ConcurrentHashMap<>();

    public BatchMonitor(MeterRegistry meterRegistry, BatchAlertNotifier batchAlertNotifier, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.batchAlertNotifier = batchAlertNotifier;
        this.clock = clock;
        // 마지막 성공 시각. 이 값이 하루 넘게 그대로면 배치가 돌지 않은 것이다
        Gauge.builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds, AtomicLong::get)
            .description("마지막으로 성공한 일일 배치 종료 시각(epoch seconds)")
            .register(meterRegistry);
    }

    public Instant start() {
        return clock.instant();
    }

    public void completed(LocalDate serviceDate, Instant startedAt, CategoryFill fill) {
        Duration duration = recordDuration(startedAt, "success");
        lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
        fill.counts().forEach(this::recordCategoryArticles);

        StringBuilder content = new StringBuilder()
            .append("✅ **일일 배치 완료** 서비스일 ").append(serviceDate)
            .append(" · ").append(format(duration)).append('\n')
            .append("저장 기사 ").append(fill.totalCount()).append("건 (카테고리ID=건수) ")
            .append(join(fill.counts()));
        if (!fill.shortage().isEmpty()) {
            content.append("\n⚠️ 최대 수 미달 카테고리: ").append(join(fill.shortage()));
        }
        batchAlertNotifier.send(content.toString());
    }

    public void failed(LocalDate serviceDate, Instant startedAt, String reason) {
        Duration duration = recordDuration(startedAt, "failure");
        batchAlertNotifier.send("❌ **일일 배치 실패** 서비스일 " + serviceDate + " · " + format(duration) + '\n'
            + "사유: " + reason + '\n'
            + "실패로 기록했으므로 같은 서비스일로 다시 실행할 수 있습니다.");
    }

    public void crashed(Throwable error) {
        batchAlertNotifier.send("🔥 **일일 배치 비정상 종료** 배치 상태를 기록하지 못했을 수 있습니다. batch_run을 확인하세요.\n"
            + error.getClass().getName() + ": " + error.getMessage());
    }

    private Duration recordDuration(Instant startedAt, String result) {
        Duration duration = Duration.between(startedAt, clock.instant());
        Timer.builder(DURATION_METRIC)
            .description("일일 배치 실행 시간")
            .tags(Tags.of("result", result))
            .register(meterRegistry)
            .record(duration);
        return duration;
    }

    private void recordCategoryArticles(Long categoryId, Long count) {
        categoryArticles.computeIfAbsent(categoryId, id -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder(CATEGORY_ARTICLES_METRIC, value, AtomicLong::get)
                .description("마지막 성공 배치의 카테고리별 저장 기사 수")
                .tags(Tags.of("categoryId", String.valueOf(id)))
                .register(meterRegistry);
            return value;
        }).set(count);
    }

    private static String format(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds / 60 + "분 " + seconds % 60 + "초";
    }

    private static String join(Map<Long, Long> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(", "));
    }
}
