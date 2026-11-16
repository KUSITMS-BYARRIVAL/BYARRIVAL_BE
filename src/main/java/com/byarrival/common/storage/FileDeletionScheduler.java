package com.byarrival.common.storage;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 남은 파일 삭제 작업을 10분마다 처리한다. 프로필 이미지를 바꾸거나 지운 뒤 이전 파일은 늦어도 10분 안에 지워진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeletionScheduler {

    private final FileDeletionService fileDeletionService;

    @Scheduled(fixedDelay = 10, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public void deletePending() {
        int deleted = fileDeletionService.deletePending();
        if (deleted > 0) {
            log.info("[FileDeletion] 저장소 파일 삭제: {}건", deleted);
        }
    }
}
