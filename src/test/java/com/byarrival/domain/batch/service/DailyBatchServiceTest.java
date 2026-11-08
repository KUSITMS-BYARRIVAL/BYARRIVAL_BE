package com.byarrival.domain.batch.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.article.service.ArticleCollectService;
import com.byarrival.domain.article.service.ArticleCollectService.CategoryFill;
import com.byarrival.domain.feed.dto.FeedCollectResult;
import com.byarrival.domain.feed.dto.FetchedFeed;
import com.byarrival.domain.feed.service.FeedCollectService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailyBatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 11, 3);

    private BatchRunService batchRunService;
    private FeedCollectService feedCollectService;
    private ArticleCollectService articleCollectService;

    @BeforeEach
    void setUp() {
        batchRunService = mock(BatchRunService.class);
        feedCollectService = mock(FeedCollectService.class);
        articleCollectService = mock(ArticleCollectService.class);
        given(articleCollectService.checkCategoryFill(any())).willReturn(new CategoryFill(List.of(), Map.of()));
    }

    @Test
    void 밤_23시45분_배치는_다음_서비스일_수집분으로_기록된다() {
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willReturn(collected());

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(feedCollectService).collect(SERVICE_DATE);
        verify(batchRunService).complete(10L);
    }

    @Test
    void 자정을_넘겨_실행해도_같은_서비스일_수집분이다() {
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willReturn(collected());

        service(LocalDateTime.of(2026, 11, 3, 0, 10)).run();

        verify(batchRunService).complete(10L);
    }

    @Test
    void 수집한_Feed마다_기사를_서비스일로_저장한다() {
        FetchedFeed economy = fetchedFeed(1L);
        FetchedFeed society = fetchedFeed(2L);
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(collected(economy, society));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(articleCollectService).collect(economy, SERVICE_DATE);
        verify(articleCollectService).collect(society, SERVICE_DATE);
        verify(batchRunService).complete(10L);
    }

    @Test
    void 한_Feed의_기사_저장이_실패해도_나머지는_저장하고_배치는_실패로_남긴다() {
        FetchedFeed broken = fetchedFeed(1L);
        FetchedFeed economy = fetchedFeed(2L);
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(collected(broken, economy));
        given(articleCollectService.collect(broken, SERVICE_DATE))
            .willThrow(new IllegalStateException("DB 연결 실패"));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(articleCollectService).collect(economy, SERVICE_DATE);
        verify(batchRunService).fail(10L, "기사 저장 실패 feedIds=[1]");
        verify(batchRunService, never()).complete(anyLong());
    }

    @Test
    void 모든_Feed_수집에_실패하면_배치를_실패로_남긴다() {
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(FeedCollectResult.of(List.of(), List.of(1L, 2L)));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService).fail(10L, "모든 Feed 수집 실패 feedIds=[1, 2]");
        verify(batchRunService, never()).complete(anyLong());
        verify(articleCollectService, never()).collect(any(), any());
    }

    @Test
    void 일부_Feed만_실패하면_수집한_Feed를_저장하고_완료한다() {
        FetchedFeed economy = fetchedFeed(2L);
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(FeedCollectResult.of(List.of(economy), List.of(1L)));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(articleCollectService).collect(economy, SERVICE_DATE);
        verify(batchRunService).complete(10L);
    }

    @Test
    void 기사가_0건인_카테고리가_있으면_배치를_실패로_남긴다() {
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(collected(fetchedFeed(1L)));
        given(articleCollectService.checkCategoryFill(SERVICE_DATE))
            .willReturn(new CategoryFill(List.of(3L), Map.of(1L, 14L)));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService).fail(10L, "기사 0건 카테고리 categoryIds=[3]");
        verify(batchRunService, never()).complete(anyLong());
    }

    @Test
    void 최대_수를_채우지_못한_카테고리만_있으면_경고하고_완료한다() {
        given(batchRunService.start(SERVICE_DATE)).willReturn(Optional.of(10L));
        given(feedCollectService.collect(SERVICE_DATE)).willReturn(collected(fetchedFeed(1L)));
        given(articleCollectService.checkCategoryFill(SERVICE_DATE))
            .willReturn(new CategoryFill(List.of(), Map.of(1L, 14L)));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService).complete(10L);
        verify(batchRunService, never()).fail(anyLong(), anyString());
    }

    @Test
    void 이미_실행_중이거나_완료된_서비스일이면_수집하지_않는다() {
        given(batchRunService.start(any())).willReturn(Optional.empty());

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(feedCollectService, never()).collect(any());
        verify(batchRunService, never()).complete(anyLong());
    }

    @Test
    void 배치가_예외로_끝나면_실패로_기록한다() {
        given(batchRunService.start(any())).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willThrow(new IllegalStateException("DB 연결 실패"));

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService).fail(10L, "DB 연결 실패");
        verify(batchRunService, never()).complete(anyLong());
    }

    @Test
    void 정상_종료하면_실패로_기록하지_않는다() {
        given(batchRunService.start(any())).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willReturn(collected());

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService, never()).fail(anyLong(), anyString());
    }

    private FeedCollectResult collected(FetchedFeed... feeds) {
        return FeedCollectResult.of(List.of(feeds), List.of());
    }

    private FetchedFeed fetchedFeed(Long feedId) {
        return new FetchedFeed(feedId, "한국경제", null, List.of());
    }

    private DailyBatchService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(KST).toInstant(), KST);
        return new DailyBatchService(batchRunService, feedCollectService, articleCollectService, clock);
    }
}
