package com.byarrival.domain.route.dto.response;

import com.byarrival.domain.route.service.RideProgress;
import io.swagger.v3.oas.annotations.media.Schema;

public record RideProgressResponse(
    @Schema(description = "전체 정거장 수 (담은 기사 수)") int totalStops,
    @Schema(description = "완독한 기사 수") int readCount,
    @Schema(description = "이어서 볼 정거장 번호 (1부터). 모두 읽었으면 마지막 정거장") int currentStop,
    @Schema(description = "남은 기사 수 (숨겼거나 삭제된 기사 제외)") int remainingStops,
    @Schema(description = "남은 기사의 예상 읽기시간 합(분, 올림)") int remainingMinutes,
    @Schema(description = "남은 기사가 없어 도착할 수 있는지") boolean arrivable
) {

    public static RideProgressResponse from(RideProgress progress) {
        return new RideProgressResponse(progress.totalStops(), progress.readCount(), progress.currentStop(),
            progress.remainingStops(), progress.remainingMinutes(), progress.arrivable());
    }
}
