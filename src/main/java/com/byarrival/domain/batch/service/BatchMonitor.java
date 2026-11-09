package com.byarrival.domain.batch.service;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import com.byarrival.domain.category.service.CategoryMappingService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 일일 배치 결과를 메트릭으로 남기고 디스코드 배치 채널에 알린다.
 * 모니터링이 실패해도 배치 결과(batch_run)가 바뀌지 않도록 예외를 밖으로 던지지 않는다.
 */
@Slf4j
@Component
public class BatchMonitor {

    static final String DURATION_METRIC = "byarrival.batch.duration";
    static final String LAST_SUCCESS_METRIC = "byarrival.batch.last.success";
    static final String CATEGORY_ARTICLES_METRIC = "byarrival.batch.category.articles";

    private final MeterRegistry meterRegistry;
    private final BatchAlertNotifier batchAlertNotifier;
    private final CategoryMappingService categoryMappingService;
    private final Clock clock;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final Map<Long, AtomicLong> categoryArticles = new ConcurrentHashMap<>();

    public BatchMonitor(MeterRegistry meterRegistry, BatchAlertNotifier batchAlertNotifier,
        CategoryMappingService categoryMappingService, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.batchAlertNotifier = batchAlertNotifier;
        this.categoryMappingService = categoryMappingService;
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
        guard(() -> recordCompleted(serviceDate, startedAt, fill));
    }

    public void failed(LocalDate serviceDate, Instant startedAt, String reason) {
        guard(() -> recordFailed(serviceDate, startedAt, reason));
    }

    public void crashed(Throwable error) {
        guard(() -> batchAlertNotifier.send("🔥 **일일 배치 비정상 종료** 배치 상태를 기록하지 못했을 수 있습니다. "
            + "batch_run을 확인하세요.\n" + error.getClass().getName() + ": " + error.getMessage()));
    }

    private void recordCompleted(LocalDate serviceDate, Instant startedAt, CategoryFill fill) {
        Duration duration = recordDuration(startedAt, "success");
        lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
        fill.counts().forEach(this::recordCategoryArticles);

        Map<Long, String> names = categoryNames();
        StringBuilder content = new StringBuilder()
            .append("✅ **일일 배치 완료** 서비스일 ").append(serviceDate)
            .append(" · ").append(format(duration)).append('\n')
            .append("저장 기사 ").append(fill.totalCount()).append("건\n")
            .append(join(fill.counts(), names));
        if (!fill.shortage().isEmpty()) {
            content.append("\n⚠️ 최대 수 미달: ").append(join(fill.shortage(), names));
        }
        batchAlertNotifier.send(content.toString());
    }

    private void recordFailed(LocalDate serviceDate, Instant startedAt, String reason) {
        Duration duration = recordDuration(startedAt, "failure");
        batchAlertNotifier.send("❌ **일일 배치 실패** 서비스일 " + serviceDate + " · " + format(duration) + '\n'
            + "사유: " + reason + '\n'
            + "실패로 기록했으므로 같은 서비스일로 다시 실행할 수 있습니다.");
    }

    private void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("[BatchMonitor] 배치 모니터링 기록 실패 - {}", e.getMessage(), e);
        }
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

    // 이름을 못 가져와도 완료 알림은 보내야 하므로 ID로 대신 표시한다
    private Map<Long, String> categoryNames() {
        try {
            return categoryMappingService.getActiveCategoryNames();
        } catch (RuntimeException e) {
            log.warn("[BatchMonitor] 카테고리 이름 조회 실패 - {}", e.getMessage());
            return Map.of();
        }
    }

    // 화면 순서(이름 조회 순서)대로 나열하고, 이름이 없는 카테고리는 ID 순으로 뒤에 붙인다
    private static String join(Map<Long, Long> counts, Map<Long, String> names) {
        List<Long> order = new ArrayList<>();
        names.keySet().stream().filter(counts::containsKey).forEach(order::add);
        counts.keySet().stream().filter(id -> !names.containsKey(id)).sorted().forEach(order::add);
        return order.stream()
            .map(id -> names.getOrDefault(id, "카테고리 " + id) + " " + counts.get(id))
            .collect(Collectors.joining(" · "));
    }
}
