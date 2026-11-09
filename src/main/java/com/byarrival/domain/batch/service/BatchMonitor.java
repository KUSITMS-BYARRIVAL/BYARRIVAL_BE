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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 일일 배치 결과를 메트릭으로 남기고 디스코드 배치 채널에 알린다.
 * 모니터링이 실패해도 배치 결과(batch_run)가 바뀌지 않도록 예외를 밖으로 던지지 않는다.
 * 예외 메시지에는 외부 URL이나 사용자 값이 섞일 수 있으므로 알림에는 예외 종류만 넣는다.
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
    private final BatchRunService batchRunService;
    private final Clock clock;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private final Map<Long, CategoryGauge> categoryArticles = new ConcurrentHashMap<>();

    public BatchMonitor(MeterRegistry meterRegistry, BatchAlertNotifier batchAlertNotifier,
        CategoryMappingService categoryMappingService, BatchRunService batchRunService, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.batchAlertNotifier = batchAlertNotifier;
        this.categoryMappingService = categoryMappingService;
        this.batchRunService = batchRunService;
        this.clock = clock;
        // 마지막 성공 시각. 이 값이 하루 넘게 그대로면 배치가 돌지 않은 것이다
        Gauge.builder(LAST_SUCCESS_METRIC, lastSuccessEpochSeconds, AtomicLong::get)
            .description("마지막으로 성공한 일일 배치 종료 시각(epoch seconds)")
            .register(meterRegistry);
    }

    // 재시작 뒤에도 마지막 성공 시각이 0으로 보이지 않도록 저장된 완료 이력으로 채운다
    @EventListener(ApplicationReadyEvent.class)
    public void restoreLastSuccess() {
        guard(() -> batchRunService.findLastCompletedAt()
            .ifPresent(finishedAt -> lastSuccessEpochSeconds.compareAndSet(0,
                finishedAt.atZone(clock.getZone()).toEpochSecond())));
    }

    public Instant start() {
        return clock.instant();
    }

    public void completed(LocalDate serviceDate, Instant startedAt, CategoryFill fill) {
        guard(() -> recordCompleted(serviceDate, startedAt, fill));
    }

    public void failed(LocalDate serviceDate, Instant startedAt, String reason) {
        guard(() -> recordFailed(serviceDate, startedAt, reason, null));
    }

    /**
     * 기사 0건 카테고리로 실패한 경우. 빈 카테고리와 최대 수 미달 카테고리를 이름으로 함께 알린다.
     */
    public void failed(LocalDate serviceDate, Instant startedAt, String reason, CategoryFill fill) {
        guard(() -> recordFailed(serviceDate, startedAt, reason, fill));
    }

    public void crashed(Throwable error) {
        guard(() -> batchAlertNotifier.send("🔥 **일일 배치 비정상 종료** 배치 상태를 기록하지 못했을 수 있습니다. "
            + "batch_run과 서버 로그를 확인하세요.\n" + error.getClass().getName()));
    }

    private void recordCompleted(LocalDate serviceDate, Instant startedAt, CategoryFill fill) {
        Duration duration = recordDuration(startedAt, "success");
        lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
        recordCategoryArticles(fill.counts());

        Map<Long, String> names = categoryNames();
        StringBuilder content = new StringBuilder()
            .append("✅ **일일 배치 완료** 서비스일 ").append(serviceDate)
            .append(" · ").append(format(duration)).append('\n')
            // 카테고리가 매핑되지 않은 검수용 기사는 추천 대상이 아니므로 세지 않는다
            .append("추천 대상 기사 ").append(fill.totalCount()).append("건\n")
            .append(join(fill.counts(), names));
        if (!fill.shortage().isEmpty()) {
            content.append("\n⚠️ 최대 수 미달: ").append(join(fill.shortage(), names));
        }
        batchAlertNotifier.send(content.toString());
    }

    private void recordFailed(LocalDate serviceDate, Instant startedAt, String reason, CategoryFill fill) {
        Duration duration = recordDuration(startedAt, "failure");
        StringBuilder content = new StringBuilder()
            .append("❌ **일일 배치 실패** 서비스일 ").append(serviceDate)
            .append(" · ").append(format(duration)).append('\n')
            .append("사유: ").append(reason);
        if (fill != null) {
            Map<Long, String> names = categoryNames();
            content.append("\n기사 0건: ").append(joinNames(fill.emptyCategoryIds(), names));
            if (!fill.shortage().isEmpty()) {
                content.append("\n⚠️ 최대 수 미달: ").append(join(fill.shortage(), names));
            }
        }
        content.append("\n실패로 기록했으므로 같은 서비스일로 다시 실행할 수 있습니다.");
        batchAlertNotifier.send(content.toString());
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

    // 이번 결과에 없는 카테고리(비활성화 등)의 게이지는 지워 이전 값이 남지 않게 한다
    private void recordCategoryArticles(Map<Long, Long> counts) {
        categoryArticles.entrySet().removeIf(entry -> {
            if (counts.containsKey(entry.getKey())) {
                return false;
            }
            meterRegistry.remove(entry.getValue().gauge());
            return true;
        });
        counts.forEach((categoryId, count) -> categoryArticles.computeIfAbsent(categoryId, id -> {
            AtomicLong value = new AtomicLong();
            Gauge gauge = Gauge.builder(CATEGORY_ARTICLES_METRIC, value, AtomicLong::get)
                .description("마지막 성공 배치의 카테고리별 저장 기사 수")
                .tags(Tags.of("categoryId", String.valueOf(id)))
                .register(meterRegistry);
            return new CategoryGauge(gauge, value);
        }).value().set(count));
    }

    private void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("[BatchMonitor] 배치 모니터링 기록 실패 - {}", e.getMessage(), e);
        }
    }

    // 이름을 못 가져와도 알림은 보내야 하므로 ID로 대신 표시한다
    private Map<Long, String> categoryNames() {
        try {
            return categoryMappingService.getActiveCategoryNames();
        } catch (RuntimeException e) {
            log.warn("[BatchMonitor] 카테고리 이름 조회 실패 - {}", e.getMessage());
            return Map.of();
        }
    }

    private static String format(Duration duration) {
        long seconds = duration.toSeconds();
        return seconds / 60 + "분 " + seconds % 60 + "초";
    }

    private static String join(Map<Long, Long> counts, Map<Long, String> names) {
        return order(counts.keySet(), names).stream()
            .map(id -> name(id, names) + " " + counts.get(id))
            .collect(Collectors.joining(" · "));
    }

    private static String joinNames(Collection<Long> categoryIds, Map<Long, String> names) {
        return order(categoryIds, names).stream()
            .map(id -> name(id, names))
            .collect(Collectors.joining(" · "));
    }

    // 화면 순서(이름 조회 순서)대로 나열하고, 이름이 없는 카테고리는 ID 순으로 뒤에 붙인다
    private static List<Long> order(Collection<Long> categoryIds, Map<Long, String> names) {
        List<Long> order = new ArrayList<>();
        names.keySet().stream().filter(categoryIds::contains).forEach(order::add);
        categoryIds.stream().filter(id -> !names.containsKey(id)).sorted().forEach(order::add);
        return order;
    }

    private static String name(Long categoryId, Map<Long, String> names) {
        return names.getOrDefault(categoryId, "카테고리 " + categoryId);
    }

    private record CategoryGauge(Gauge gauge, AtomicLong value) {
    }
}
