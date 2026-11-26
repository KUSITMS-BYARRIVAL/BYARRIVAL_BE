package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.user.entity.RestrictionPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원 이용 제한(MEMBER-03). 기간과 사유는 필수다.
 *
 * @param period 제한 기간
 * @param reason 사유. 최대 100자. 회원의 안내 화면에 그대로 보인다
 */
public record AdminMemberRestrictRequest(
    @NotNull RestrictionPeriod period,
    @NotBlank @Size(max = 100) String reason
) {
}
