package com.byarrival.domain.batch.dto.response;

import com.byarrival.domain.batch.entity.BatchRun;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 다음 서비스일 배치 상태. 어드민 새로고침 버튼의 비활성화와 결과 안내에 쓴다.
 */
public record BatchRefreshResponse(
    @Schema(description = "새로고침 대상 서비스일(다음 서비스일)") LocalDate serviceDate,
    @Schema(description = "NONE(아직 배치 없음), RUNNING(실행 중, 버튼 비활성화), COMPLETED, FAILED") State status,
    @Schema(description = "가장 최근 실행의 시작 시각. NONE이면 null") LocalDateTime startedAt,
    @Schema(description = "가장 최근 실행의 종료 시각. 실행 중이거나 NONE이면 null") LocalDateTime finishedAt,
    @Schema(description = "추천 대상 기사 수. COMPLETED일 때만 값이 있다") Long recommendableCount
) {

    public enum State {
        NONE,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public static BatchRefreshResponse none(LocalDate serviceDate) {
        return new BatchRefreshResponse(serviceDate, State.NONE, null, null, null);
    }

    public static BatchRefreshResponse of(BatchRun batchRun, Long recommendableCount) {
        State state = switch (batchRun.getStatus()) {
            case RUNNING -> State.RUNNING;
            // 대체된 실행은 최신 실행이 될 수 없지만, 그렇더라도 완료됐던 실행이다
            case COMPLETED, REPLACED -> State.COMPLETED;
            case FAILED -> State.FAILED;
        };
        return new BatchRefreshResponse(batchRun.getServiceDate(), state, batchRun.getStartedAt(),
            batchRun.getFinishedAt(), state == State.COMPLETED ? recommendableCount : null);
    }
}
