package com.byarrival.domain.notification.auto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 자동 알림 문구. 제목은 유형별 후보 중 매번 무작위로 고른다.
 */
@Component
public class NotificationMessages {

    static final int MAX_TITLE_LENGTH = 100;

    static final List<String> ROUTE_READY_TITLES = List.of(
        "오늘의 노선이 준비됐어요 🚇",
        "오늘도 출발 준비 완료 🚇",
        "오늘 출근길도 저희가 짜뒀어요 🚇",
        "오늘 출근길도 가볍게 출발 🚇");
    static final String ROUTE_READY_BODY = "오늘의 노선, 도착까지 시작해볼까요?";

    static final String ROUTE_INCOMPLETE_REMAINING_TITLE = "아직 %d정거장 남았어요 🚉";
    static final List<String> ROUTE_INCOMPLETE_TITLES = List.of(
        "고객님, 아직 종점이 아닙니다 📢",
        "우리 아직 도착 안 했잖아요! 👀",
        "출근길에 두고 간 이야기가 있어요 💬");
    static final String ROUTE_INCOMPLETE_BODY = "오늘의 노선, 도착까지 이어가볼까요?";

    static final List<String> STREAK_MILESTONE_TITLES = List.of(
        "%d일 연속 출근길 달성! 📈",
        "%d일 연속 탑승! 오늘도 같이 출근 중 🚇",
        "%d일 연속 탑승! 우리 매일 보는 사이 💚",
        "%d일 연속 탑승? 개근상 드릴게요 🏆");
    static final String STREAK_MILESTONE_BODY = "최장 기록 %d일까지 %d일 남았어요.";
    static final String STREAK_MILESTONE_RECORD_BODY = "최장 기록이에요!";

    static final List<String> SMALLTALK_TITLES = List.of(
        "오늘의 스몰토크가 올라왔어요 💬",
        "오늘 써먹을 한마디 도착! 💬",
        "오늘 점심시간, 이 얘기 어때요? 👀",
        "오늘의 스몰토크 소재 투척! 👀");

    // %1$d: 오늘 도착하면 이어지는 일수, %2$d: 지금까지 이어진 일수
    static final List<String> STREAK_BREAK_TITLES = List.of(
        "오늘도 도착하면 %1$d일 연속이에요 🔥",
        "%1$d일째 출근길, 오늘도 같이 가요! 🥹",
        "%2$d일 연속 탑승 기록, 여기서 멈추기엔 아깝잖아요 👀",
        "단골 승객님, 오늘은 어디 계세요? 🚇");
    static final String STREAK_BREAK_BODY = "오늘의 노선이 아직 기다리고 있어요.";

    static final String POPULAR_REGION_JOB_BODY = "지금 %s %s에서 많이 보고 있어요.";
    static final String POPULAR_JOB_BODY = "오늘 %s 직군에서 많이 보고 있어요.";
    static final String POPULAR_REGION_BODY = "지금 %s 출근길에서 많이 보고 있어요.";

    private final Random random;

    public NotificationMessages(@Qualifier("notificationRandom") Random random) {
        this.random = random;
    }

    public String routeReadyTitle() {
        return pick(ROUTE_READY_TITLES);
    }

    /**
     * @param remainingStops 아직 읽지 않은 담은 기사 수. 0이면 남은 정거장 수를 말하는 제목은 고르지 않는다
     */
    public String routeIncompleteTitle(int remainingStops) {
        List<String> titles = new ArrayList<>(ROUTE_INCOMPLETE_TITLES);
        if (remainingStops > 0) {
            titles.add(ROUTE_INCOMPLETE_REMAINING_TITLE.formatted(remainingStops));
        }
        return pick(titles);
    }

    public String streakMilestoneTitle(int streakDays) {
        return pick(STREAK_MILESTONE_TITLES).formatted(streakDays);
    }

    public String streakMilestoneBody(int streakDays, int longestDays) {
        if (streakDays >= longestDays) {
            return STREAK_MILESTONE_RECORD_BODY;
        }
        return STREAK_MILESTONE_BODY.formatted(longestDays, longestDays - streakDays);
    }

    public String smalltalkTitle() {
        return pick(SMALLTALK_TITLES);
    }

    /**
     * @param streakDays 어제(직전 평일)까지 이어진 일수
     */
    public String streakBreakTitle(int streakDays) {
        return pick(STREAK_BREAK_TITLES).formatted(streakDays + 1, streakDays);
    }

    /**
     * 기사 제목이 알림 제목 길이를 넘으면 줄인다.
     */
    public String popularTitle(String headline) {
        if (headline.length() <= MAX_TITLE_LENGTH) {
            return headline;
        }
        return headline.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    public String popularBody(PopularScope scope, String regionName, String jobGroupName) {
        return switch (scope) {
            case REGION_AND_JOB -> POPULAR_REGION_JOB_BODY.formatted(regionName, jobGroupName);
            case JOB -> POPULAR_JOB_BODY.formatted(jobGroupName);
            case REGION -> POPULAR_REGION_BODY.formatted(regionName);
        };
    }

    private String pick(List<String> candidates) {
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 인기 기사를 모은 범위. 앞 범위에 후보가 없으면 다음 범위로 넓힌다.
     */
    public enum PopularScope {
        REGION_AND_JOB,
        JOB,
        REGION
    }
}
