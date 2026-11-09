package com.byarrival.domain.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.byarrival.common.monitoring.BatchAlertNotifier;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import com.byarrival.domain.category.service.CategoryMappingService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchMonitorTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 10);
    private static final Instant ENDED_AT = LocalDateTime.of(2026, 11, 9, 23, 48, 12).atZone(KST).toInstant();
    private static final Instant STARTED_AT = ENDED_AT.minus(Duration.ofSeconds(192));

    private SimpleMeterRegistry meterRegistry;
    private BatchAlertNotifier batchAlertNotifier;
    private CategoryMappingService categoryMappingService;
    private BatchMonitor batchMonitor;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        batchAlertNotifier = mock(BatchAlertNotifier.class);
        categoryMappingService = mock(CategoryMappingService.class);
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(2L, "경제");
        names.put(1L, "정치");
        given(categoryMappingService.getActiveCategoryNames()).willReturn(names);
        batchMonitor = new BatchMonitor(meterRegistry, batchAlertNotifier, categoryMappingService,
            Clock.fixed(ENDED_AT, KST));
    }

    @Test
    void 완료하면_실행_시간과_카테고리별_기사_수를_메트릭으로_남긴다() {
        batchMonitor.completed(SERVICE_DATE, STARTED_AT, fill());

        Timer timer = meterRegistry.get(BatchMonitor.DURATION_METRIC).tag("result", "success").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(192);
        assertThat(meterRegistry.get(BatchMonitor.CATEGORY_ARTICLES_METRIC).tag("categoryId", "2").gauge().value())
            .isEqualTo(15);
        assertThat(meterRegistry.get(BatchMonitor.LAST_SUCCESS_METRIC).gauge().value())
            .isEqualTo(ENDED_AT.getEpochSecond());
    }

    @Test
    void 완료하면_저장_건수와_미달_카테고리를_배치_채널에_알린다() {
        batchMonitor.completed(SERVICE_DATE, STARTED_AT, fill());

        assertThat(sentContent())
            .contains("일일 배치 완료", "2026-11-10", "3분 12초", "저장 기사 35건", "경제 15 · 정치 20",
                "최대 수 미달: 경제 15");
    }

    @Test
    void 카테고리_이름을_가져오지_못하면_ID로_표시한다() {
        given(categoryMappingService.getActiveCategoryNames()).willThrow(new IllegalStateException("DB 오류"));

        batchMonitor.completed(SERVICE_DATE, STARTED_AT, fill());

        assertThat(sentContent()).contains("카테고리 1 20 · 카테고리 2 15");
    }

    @Test
    void 실패하면_실패_메트릭과_사유를_남긴다() {
        batchMonitor.failed(SERVICE_DATE, STARTED_AT, "기사 0건 카테고리 categoryIds=[3]");

        assertThat(meterRegistry.get(BatchMonitor.DURATION_METRIC).tag("result", "failure").timer().count())
            .isEqualTo(1);
        assertThat(meterRegistry.get(BatchMonitor.LAST_SUCCESS_METRIC).gauge().value()).isZero();
        assertThat(sentContent()).contains("일일 배치 실패", "2026-11-10", "기사 0건 카테고리 categoryIds=[3]");
    }

    @Test
    void 비정상_종료는_예외_정보와_함께_알린다() {
        batchMonitor.crashed(new IllegalStateException("DB 연결 실패"));

        assertThat(sentContent()).contains("비정상 종료", "java.lang.IllegalStateException", "DB 연결 실패");
    }

    @Test
    void 알림이_실패해도_예외를_던지지_않는다() {
        willThrow(new IllegalStateException("알림 실패")).given(batchAlertNotifier).send(anyString());

        assertThatCode(() -> batchMonitor.completed(SERVICE_DATE, STARTED_AT, fill())).doesNotThrowAnyException();
        assertThatCode(() -> batchMonitor.failed(SERVICE_DATE, STARTED_AT, "사유")).doesNotThrowAnyException();
        assertThatCode(() -> batchMonitor.crashed(new IllegalStateException("x"))).doesNotThrowAnyException();
    }

    private CategoryFill fill() {
        return new CategoryFill(List.of(), Map.of(2L, 15L), Map.of(1L, 20L, 2L, 15L));
    }

    private String sentContent() {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(batchAlertNotifier).send(content.capture());
        return content.getValue();
    }
}
