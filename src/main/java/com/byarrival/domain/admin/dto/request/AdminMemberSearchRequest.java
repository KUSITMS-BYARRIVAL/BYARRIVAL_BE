package com.byarrival.domain.admin.dto.request;

import com.byarrival.domain.admin.entity.AdminMemberStatus;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 회원 목록 검색 조건(MEMBER-01). 값을 보내지 않은 조건은 적용하지 않는다.
 *
 * @param keyword    닉네임 또는 회원번호(앞의 # 생략 가능)
 * @param travelType 유형
 * @param provider   가입 수단
 * @param status     회원 상태(정상·이용 제한)
 * @param page       1부터 시작. 기본 1
 */
public record AdminMemberSearchRequest(
    @Size(max = 30) String keyword,
    TravelType travelType,
    SocialProvider provider,
    AdminMemberStatus status,
    @Min(1) Integer page
) {

    public AdminMemberSearchRequest {
        page = page == null ? Integer.valueOf(1) : page;
    }
}
