package com.byarrival.domain.user.dto.response;

import com.byarrival.domain.user.entity.UserRestriction;
import java.time.LocalDateTime;

/**
 * 지금 적용 중인 이용 제한. 앱은 이 값이 있으면 이용 제한 안내 화면을 보여 준다.
 *
 * @param reason   운영자가 적은 사유
 * @param startsAt 제한을 시작한 시각
 * @param endsAt   제한이 끝나는 시각. 영구 제한이면 null
 */
public record UserRestrictionResponse(String reason, LocalDateTime startsAt, LocalDateTime endsAt) {

    public static UserRestrictionResponse from(UserRestriction restriction) {
        return new UserRestrictionResponse(restriction.getReason(), restriction.getStartsAt(),
            restriction.getEndsAt());
    }
}
