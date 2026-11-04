package com.byarrival.domain.batch.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.domain.feed.service.FeedCollectService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DailyBatchServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private BatchRunService batchRunService;
    private FeedCollectService feedCollectService;

    @BeforeEach
    void setUp() {
        batchRunService = mock(BatchRunService.class);
        feedCollectService = mock(FeedCollectService.class);
    }

    @Test
    void 밤_23시45분_배치는_다음_서비스일_수집분으로_기록된다() {
        given(batchRunService.start(LocalDate.of(2026, 11, 3))).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willReturn(List.of());

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(feedCollectService).collect(LocalDate.of(2026, 11, 3));
        verify(batchRunService).complete(10L);
    }

    @Test
    void 자정을_넘겨_실행해도_같은_서비스일_수집분이다() {
        given(batchRunService.start(LocalDate.of(2026, 11, 3))).willReturn(Optional.of(10L));
        given(feedCollectService.collect(any())).willReturn(List.of());

        service(LocalDateTime.of(2026, 11, 3, 0, 10)).run();

        verify(batchRunService).complete(10L);
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
        given(feedCollectService.collect(any())).willReturn(List.of());

        service(LocalDateTime.of(2026, 11, 2, 23, 45)).run();

        verify(batchRunService, never()).fail(anyLong(), anyString());
    }

    private DailyBatchService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(KST).toInstant(), KST);
        return new DailyBatchService(batchRunService, feedCollectService, clock);
    }
}
