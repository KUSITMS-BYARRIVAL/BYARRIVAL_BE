package com.byarrival.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.byarrival.common.monitoring.ServerErrorNotifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.core.exception.SdkClientException;

class FileDeletionServiceTest {

    private static final String URL = "https://cdn.test/local/profile/1/a.jpg";

    private FileDeletionTaskRepository repository;
    private FileStorage fileStorage;
    private ServerErrorNotifier serverErrorNotifier;
    private FileDeletionService service;

    @BeforeEach
    void setUp() {
        repository = mock(FileDeletionTaskRepository.class);
        fileStorage = mock(FileStorage.class);
        serverErrorNotifier = mock(ServerErrorNotifier.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        service = new FileDeletionService(repository, fileStorage, serverErrorNotifier, clock,
            mock(PlatformTransactionManager.class));
    }

    @Test
    void 파일을_지우면_작업을_없앤다() {
        FileDeletionTask task = task(1L, 0);
        given(repository.findAllByOrderByIdAsc(any(Pageable.class))).willReturn(List.of(task));

        int deleted = service.deletePending();

        assertThat(deleted).isEqualTo(1);
        verify(fileStorage).delete(URL);
        verify(repository).deleteById(1L);
    }

    @Test
    void 지우지_못하면_작업을_남기고_실패_횟수를_늘린다() {
        FileDeletionTask task = task(1L, 0);
        given(repository.findAllByOrderByIdAsc(any(Pageable.class))).willReturn(List.of(task));
        given(repository.findById(1L)).willReturn(Optional.of(task));
        willThrow(SdkClientException.create("S3 연결 실패")).given(fileStorage).delete(URL);

        int deleted = service.deletePending();

        assertThat(deleted).isZero();
        assertThat(task.getAttempts()).isEqualTo(1);
        assertThat(task.getLastFailedAt()).isNotNull();
        verify(repository, never()).deleteById(any());
        verify(serverErrorNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void 정해진_횟수만큼_실패하면_서버_에러_채널에_한_번_알린다() {
        FileDeletionTask task = task(1L, FileDeletionService.ALERT_ATTEMPTS - 1);
        given(repository.findAllByOrderByIdAsc(any(Pageable.class))).willReturn(List.of(task));
        given(repository.findById(1L)).willReturn(Optional.of(task));
        willThrow(SdkClientException.create("S3 연결 실패")).given(fileStorage).delete(URL);

        service.deletePending();
        service.deletePending();

        verify(serverErrorNotifier).notify(eq(FileDeletionService.ALERT_METHOD), eq(FileDeletionService.ALERT_URI),
            any(SdkClientException.class));
        assertThat(task.getAttempts()).isEqualTo(FileDeletionService.ALERT_ATTEMPTS + 1);
    }

    @Test
    void 한_파일이_실패해도_나머지는_계속_지운다() {
        FileDeletionTask failing = task(1L, 0);
        FileDeletionTask other = taskOf(2L, "https://cdn.test/local/profile/2/b.jpg");
        given(repository.findAllByOrderByIdAsc(any(Pageable.class))).willReturn(List.of(failing, other));
        given(repository.findById(1L)).willReturn(Optional.of(failing));
        willThrow(SdkClientException.create("S3 연결 실패")).given(fileStorage).delete(URL);

        int deleted = service.deletePending();

        assertThat(deleted).isEqualTo(1);
        verify(repository).deleteById(2L);
    }

    private static FileDeletionTask task(Long id, int attempts) {
        FileDeletionTask task = taskOf(id, URL);
        ReflectionTestUtils.setField(task, "attempts", attempts);
        return task;
    }

    private static FileDeletionTask taskOf(Long id, String url) {
        FileDeletionTask task = FileDeletionTask.of(url);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
