package com.byarrival.domain.notification.auto;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 자동 알림 발송 조건(DCK-34). 시각 기준 알림은 해당 요일의 출근 시각을 기준으로 한다.
 *
 * @param enabled                    false면 시각 기준 자동 알림 작업을 돌리지 않는다
 * @param graceMinutes               1분 단위 작업을 건너뛰어도 이 시간 안이면 보낸다
 * @param routeReadyBeforeMinutes    오늘의 노선 준비: 출근 시각 몇 분 전
 * @param routeIncompleteIdleMinutes 미완주 리마인드: 마지막 활동 후 몇 분
 * @param streakBreakAfterMinutes    스트릭 이탈: 출근 시각 몇 분 후
 * @param streakBreakMinDays         스트릭 이탈: 이 일수 이상 이어졌을 때만 보낸다
 * @param streakMilestones           연속 출근길 달성: 알림을 보내는 연속 일수
 * @param popularAfterMinutes        인기 기사: 출근 시각 몇 분 후
 * @param popularMinUsers            인기 기사: 그날 이 기사를 담은 사용자가 이 수 이상이어야 후보다
 * @param popularTopSize             인기 기사: 후보로 보는 순위 수
 */
@ConfigurationProperties(prefix = "notification.auto")
public record AutoNotificationProperties(
    boolean enabled,
    int graceMinutes,
    int routeReadyBeforeMinutes,
    int routeIncompleteIdleMinutes,
    int streakBreakAfterMinutes,
    int streakBreakMinDays,
    List<Integer> streakMilestones,
    int popularAfterMinutes,
    int popularMinUsers,
    int popularTopSize
) {

    public AutoNotificationProperties {
        if (graceMinutes < 1 || popularMinUsers < 1 || popularTopSize < 1) {
            throw new IllegalArgumentException("notification.auto의 grace-minutes, popular-min-users, "
                + "popular-top-size는 1 이상이어야 합니다.");
        }
        streakMilestones = streakMilestones == null ? List.of() : List.copyOf(streakMilestones);
    }
}
