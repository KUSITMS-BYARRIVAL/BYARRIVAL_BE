package com.byarrival.domain.admin.dto.response;

import com.byarrival.domain.admin.entity.AdminMemberStatus;
import com.byarrival.domain.admin.repository.AdminMemberRepository.MemberRow;
import com.byarrival.domain.region.dto.response.RegionResponse;
import com.byarrival.domain.route.service.RouteStreakService.StreakDays;
import com.byarrival.domain.user.dto.response.DailyCommuteResponse;
import com.byarrival.domain.user.entity.JobGroup;
import com.byarrival.domain.user.entity.RestrictionPeriod;
import com.byarrival.domain.user.entity.SocialProvider;
import com.byarrival.domain.user.entity.TravelType;
import com.byarrival.domain.user.entity.UserRestriction;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 회원 API 응답 모음(ADM-S07). 소셜 계정 식별자와 토큰 같은 값은 넣지 않는다.
 */
public final class AdminMemberResponses {

    private AdminMemberResponses() {
    }

    /**
     * @param page          현재 페이지(1부터)
     * @param totalElements 검색 조건에 맞는 회원 수
     */
    public record Page(List<Item> members, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    }

    /**
     * @param jobGroup 처음 고른 직군. 고르지 않았으면 null
     * @param region   근무 지역. 고르지 않았으면 null
     * @param status   정상 또는 이용 제한
     */
    public record Item(Long memberId, String nickname, SocialProvider provider, TravelType travelType,
                       LocalDateTime joinedAt, Job jobGroup, Region region, AdminMemberStatus status) {

        public static Item from(MemberRow row) {
            return new Item(row.id(), row.nickname(), row.provider(), row.travelType(), row.joinedAt(),
                Job.from(row.jobGroup()), Region.of(row.sidoName(), row.sigunguName()), row.status());
        }
    }

    public record Job(JobGroup code, String name) {

        public static Job from(JobGroup jobGroup) {
            return jobGroup == null ? null : new Job(jobGroup, jobGroup.getDisplayName());
        }
    }

    /**
     * @param sidoName    시/도 이름(예: 서울)
     * @param sigunguName 시/군/구 이름(예: 강남구)
     */
    public record Region(String sidoName, String sigunguName) {

        public static Region of(String sidoName, String sigunguName) {
            return sigunguName == null ? null : new Region(sidoName, sigunguName);
        }

        public static Region from(RegionResponse region) {
            return region == null ? null : new Region(region.sidoName(), region.sigunguName());
        }
    }

    public record Streak(int current, int longest) {

        public static Streak from(StreakDays days) {
            return new Streak(days.current(), days.longest());
        }
    }

    /**
     * @param lastRideAt            가장 최근에 출발(탑승)한 시각. 없으면 null
     * @param defaultCommuteMinutes 기본 이동시간(분)
     * @param weeklyCommute         이동하는 요일만 담는다
     * @param interestCategories    관심 분야 이름
     * @param status                정상 또는 이용 제한
     * @param restriction           지금 적용 중인 이용 제한. 없으면 null
     * @param restrictionHistory    이용 제한 조치 이력. 최근 순
     */
    public record Detail(Long memberId, String nickname, SocialProvider provider, TravelType travelType,
                         LocalDateTime joinedAt, boolean onboardingCompleted, LocalDateTime lastRideAt,
                         Integer defaultCommuteMinutes, List<DailyCommuteResponse> weeklyCommute, Job jobGroup,
                         Region region, List<String> interestCategories, Streak streak, AdminMemberStatus status,
                         Restriction restriction, List<RestrictionHistory> restrictionHistory) {
    }

    /**
     * 이용 제한·해제 결과(MEMBER-03). 바뀐 회원 상태와 조치 이력을 돌려준다.
     *
     * @param restriction        지금 적용 중인 이용 제한. 없으면 null
     * @param restrictionHistory 이용 제한 조치 이력. 최근 순
     */
    public record RestrictionResult(Long memberId, AdminMemberStatus status, Restriction restriction,
                                    List<RestrictionHistory> restrictionHistory) {
    }

    /**
     * @param endsAt 제한이 끝나는 시각. 영구 제한이면 null
     */
    public record Restriction(Long restrictionId, RestrictionPeriod period, String reason, LocalDateTime startsAt,
                              LocalDateTime endsAt) {

        public static Restriction from(UserRestriction restriction) {
            return new Restriction(restriction.getId(), restriction.getPeriod(), restriction.getReason(),
                restriction.getStartsAt(), restriction.getEndsAt());
        }
    }

    /**
     * @param endsAt       제한이 끝나는(끝난) 시각. 영구 제한이면 null
     * @param restrictedBy 제한한 운영자 이메일
     * @param releasedAt   운영자가 기간 전에 해제한 시각. 해제하지 않았으면 null
     * @param releasedBy   해제한 운영자 이메일. 해제하지 않았으면 null
     * @param active       지금 적용 중인지
     */
    public record RestrictionHistory(Long restrictionId, RestrictionPeriod period, String reason,
                                     LocalDateTime startsAt, LocalDateTime endsAt, String restrictedBy,
                                     LocalDateTime releasedAt, String releasedBy, boolean active) {
    }
}
